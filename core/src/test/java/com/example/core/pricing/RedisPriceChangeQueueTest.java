package com.example.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.core.testsupport.RedisTestServer;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

/** Tests the {@link RedisPriceChangeQueue} lists against a real Redis (Docker). */
class RedisPriceChangeQueueTest {

    private RedissonClient redisson;
    private RedisPriceChangeQueue queue;

    @BeforeEach
    void setUp() {
        redisson = RedisTestServer.newEmptyClient();
        queue = new RedisPriceChangeQueue(redisson, "test:");
    }

    @AfterEach
    void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void messagesComeOutInTheOrderTheyWentIn() throws Exception {
        String first = queue.send("A", 10);
        String second = queue.send("B", 20);

        PriceChange one = PriceChange.fromJson(queue.receive(1, TimeUnit.SECONDS));
        PriceChange two = PriceChange.fromJson(queue.receive(1, TimeUnit.SECONDS));

        assertEquals(first, one.getId());
        assertEquals("A", one.getSku());
        assertEquals(10, one.getPercent());
        assertEquals(second, two.getId());
        assertNull(queue.receive(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void aReceivedMessageStaysInProcessingUntilAcknowledged() throws Exception {
        queue.send("A", 10);

        String raw = queue.receive(1, TimeUnit.SECONDS);
        assertEquals(0, queue.size());
        assertEquals(1, queue.processingSize());

        queue.acknowledge(raw);
        assertEquals(0, queue.processingSize());
    }

    @Test
    void retryRequeuesTheNextAttempt() throws Exception {
        queue.send("A", 10);
        String raw = queue.receive(1, TimeUnit.SECONDS);
        PriceChange message = PriceChange.fromJson(raw);

        queue.retry(raw, message.nextAttempt());

        assertEquals(0, queue.processingSize());
        assertEquals(message.nextAttempt(), PriceChange.fromJson(queue.receive(1, TimeUnit.SECONDS)));
    }

    @Test
    void deadLetterKeepsTheMessageAside() throws Exception {
        queue.send("A", 10);
        String raw = queue.receive(1, TimeUnit.SECONDS);

        queue.deadLetter(raw);

        assertEquals(0, queue.processingSize());
        assertEquals(0, queue.size());
        assertEquals(Collections.singletonList(raw), queue.deadLetters());
    }

    @Test
    void recoverPutsUnacknowledgedMessagesBack() throws Exception {
        queue.send("A", 10);
        queue.send("B", 20);
        String a = queue.receive(1, TimeUnit.SECONDS);
        String b = queue.receive(1, TimeUnit.SECONDS);
        // ... and the consumer stops here without acknowledging.

        assertEquals(2, queue.recover());

        assertEquals(0, queue.processingSize());
        assertEquals(2, queue.size());
        assertEquals(a, queue.receive(1, TimeUnit.SECONDS));
        assertEquals(b, queue.receive(1, TimeUnit.SECONDS));
        assertEquals(0, new RedisPriceChangeQueue(redisson, "other:").recover());
    }
}
