package com.example.core.pricing;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A background thread that takes price changes off the Redis queue and passes
 * them to the {@link PriceChangeHandler}. It replaces the container's
 * message-driven bean delivery.
 * <p>
 * Each message is acknowledged only after it has been handled, so a crash
 * mid-message leaves it in the processing list, and {@link #start()} puts it
 * back on the queue next time. A message that needs a retry goes back on the
 * queue with its attempt count raised, up to {@code maxAttempts} deliveries in
 * all. After that it goes to the dead-letter list.
 */
public class PriceChangeWorker implements AutoCloseable {

    private static final long POLL_SECONDS = 1;

    private final RedisPriceChangeQueue queue;
    private final PriceChangeHandler handler;
    private final int maxAttempts;
    private volatile boolean running;
    private Thread thread;

    public PriceChangeWorker(RedisPriceChangeQueue queue, PriceChangeHandler handler, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.queue = Objects.requireNonNull(queue, "queue");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.maxAttempts = maxAttempts;
    }

    /** Recovers unacknowledged messages, then starts consuming on a daemon thread. */
    public synchronized void start() {
        if (running) {
            return;
        }
        int recovered = queue.recover();
        if (recovered > 0) {
            log("Recovered " + recovered + " unacknowledged message(s)");
        }
        running = true;
        thread = new Thread(this::run, "price-change-worker");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isRunning() {
        return running;
    }

    /** Stops consuming and waits (briefly) for the current message to finish. */
    @Override
    public synchronized void close() {
        running = false;
        if (thread != null) {
            try {
                // The poll times out after POLL_SECONDS, so the loop sees the flag soon.
                thread.join(TimeUnit.SECONDS.toMillis(POLL_SECONDS * 5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void run() {
        log("Started");
        while (running) {
            try {
                String raw = queue.receive(POLL_SECONDS, TimeUnit.SECONDS);
                if (raw != null) {
                    process(raw);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                // Most likely Redis is unreachable. Anything received but not
                // acknowledged stays in the processing list for recovery.
                log("Queue error, pausing: " + e);
                pause();
            }
        }
        log("Stopped");
    }

    /** Handles one received message and acknowledges, retries or dead-letters it. */
    void process(String raw) {
        PriceChange message;
        try {
            message = PriceChange.fromJson(raw);
        } catch (IllegalArgumentException e) {
            // Like a JMS message of the wrong type: it can never be processed.
            log("Discarding unreadable message: " + raw);
            queue.acknowledge(raw);
            return;
        }
        switch (handler.handle(message)) {
            case RETRY:
                if (message.getAttempt() + 1 >= maxAttempts) {
                    log("Giving up on " + message.getId() + " after " + maxAttempts + " attempt(s); dead-lettered");
                    queue.deadLetter(raw);
                } else {
                    queue.retry(raw, message.nextAttempt());
                }
                break;
            case APPLIED:
            case DISCARDED:
            default:
                queue.acknowledge(raw);
                break;
        }
    }

    private void pause() {
        try {
            TimeUnit.SECONDS.sleep(POLL_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static void log(String event) {
        System.out.println("[PriceChangeWorker] " + event);
    }
}
