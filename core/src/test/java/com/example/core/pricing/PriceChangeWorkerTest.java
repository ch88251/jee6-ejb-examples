package com.example.core.pricing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.core.pricing.PriceChangeHandler.Outcome;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link PriceChangeWorker} with the queue and handler mocked:
 * what happens to a message for each outcome, and the thread's start and stop.
 * {@code RedisPriceChangeQueueTest} covers the queue itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceChangeWorkerTest {

    @Mock private RedisPriceChangeQueue queue;
    @Mock private PriceChangeHandler handler;

    private PriceChangeWorker worker(int maxAttempts) {
        return new PriceChangeWorker(queue, handler, maxAttempts);
    }

    @Test
    void appliedAndDiscardedMessagesAreAcknowledged() {
        PriceChange applied = PriceChange.of("A", 10);
        PriceChange discarded = PriceChange.of("B", 0);
        when(handler.handle(applied)).thenReturn(Outcome.APPLIED);
        when(handler.handle(discarded)).thenReturn(Outcome.DISCARDED);

        worker(3).process(applied.toJson());
        worker(3).process(discarded.toJson());

        verify(queue).acknowledge(applied.toJson());
        verify(queue).acknowledge(discarded.toJson());
        verify(queue, never()).retry(anyString(), any());
    }

    @Test
    void retryPutsTheNextAttemptBackOnTheQueue() {
        PriceChange message = PriceChange.of("A", 10);
        when(handler.handle(message)).thenReturn(Outcome.RETRY);

        worker(3).process(message.toJson());

        verify(queue).retry(message.toJson(), message.nextAttempt());
        verify(queue, never()).deadLetter(anyString());
    }

    @Test
    void theLastAttemptGoesToTheDeadLetterList() {
        PriceChange third = PriceChange.of("A", 10).nextAttempt().nextAttempt();
        when(handler.handle(third)).thenReturn(Outcome.RETRY);

        worker(3).process(third.toJson());

        verify(queue).deadLetter(third.toJson());
        verify(queue, never()).retry(anyString(), any());
    }

    @Test
    void unreadableMessagesAreAcknowledgedWithoutHandling() {
        worker(3).process("not json");

        verify(queue).acknowledge("not json");
        verify(handler, never()).handle(any());
    }

    @Test
    void theThreadRecoversThenConsumesUntilClosed() throws Exception {
        PriceChange message = PriceChange.of("A", 10);
        when(queue.recover()).thenReturn(2);
        when(queue.receive(anyLong(), any(TimeUnit.class))).thenReturn(message.toJson()).thenReturn(null);
        when(handler.handle(message)).thenReturn(Outcome.APPLIED);
        PriceChangeWorker worker = worker(3);

        worker.start();
        worker.start();   // a second start does nothing
        assertTrue(worker.isRunning());
        verify(queue, timeout(5000)).acknowledge(message.toJson());
        worker.close();

        assertFalse(worker.isRunning());
        verify(queue, times(1)).recover();
        worker.close();   // closing twice is harmless
    }

    @Test
    void queueErrorsPauseTheLoopInsteadOfEndingIt() throws Exception {
        PriceChange message = PriceChange.of("A", 10);
        when(queue.receive(anyLong(), any(TimeUnit.class)))
                .thenThrow(new IllegalStateException("Redis is down"))
                .thenReturn(message.toJson())
                .thenReturn(null);
        when(handler.handle(message)).thenReturn(Outcome.APPLIED);
        PriceChangeWorker worker = worker(3);

        worker.start();
        verify(queue, timeout(5000)).acknowledge(message.toJson());
        worker.close();
    }

    @Test
    void anInterruptStopsTheLoop() throws Exception {
        when(queue.receive(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        PriceChangeWorker worker = worker(3);

        worker.start();
        verify(queue, timeout(5000)).receive(anyLong(), any(TimeUnit.class));
        worker.close();
    }

    @Test
    void needsAtLeastOneAttempt() {
        assertThrows(IllegalArgumentException.class, () -> worker(0));
    }
}
