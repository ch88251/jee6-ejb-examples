package com.example.core.pricing;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDeque;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * A reliable work queue made of three Redis lists. It replaces the JMS queue.
 * <ul>
 *   <li>{@code <prefix>pricing:queue}: messages waiting. Producers push on the
 *       left, the consumer takes from the right, so it is first in, first out.</li>
 *   <li>{@code <prefix>pricing:processing}: messages being handled. A message
 *       moves here in the same atomic step that takes it off the queue
 *       ({@code BRPOPLPUSH}), and stays until it is {@link #acknowledge acknowledged}.
 *       If the consumer dies in between, {@link #recover} puts it back on the
 *       queue, so nothing is lost. Messages are delivered at least once.</li>
 *   <li>{@code <prefix>pricing:dead}: messages that failed too many times, kept
 *       for someone to look at.</li>
 * </ul>
 * This assumes one consumer per queue. With several, give each its own
 * processing list, or use Redis Streams and consumer groups.
 */
public class RedisPriceChangeQueue implements PriceChangeQueue {

    private final RBlockingDeque<String> queue;
    private final RDeque<String> processing;
    private final RList<String> dead;

    public RedisPriceChangeQueue(RedissonClient redisson, String keyPrefix) {
        Objects.requireNonNull(redisson, "redisson");
        this.queue = redisson.getBlockingDeque(keyPrefix + "pricing:queue", StringCodec.INSTANCE);
        this.processing = redisson.getDeque(keyPrefix + "pricing:processing", StringCodec.INSTANCE);
        this.dead = redisson.getList(keyPrefix + "pricing:dead", StringCodec.INSTANCE);
    }

    @Override
    public String send(String sku, int percent) {
        PriceChange message = PriceChange.of(sku, percent);
        queue.addFirst(message.toJson());
        return message.getId();
    }

    /**
     * Waits up to {@code timeout} for a message and moves it to the processing
     * list. Returns the raw message, or {@code null} if none arrived.
     */
    public String receive(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.pollLastAndOfferFirstTo(processing.getName(), timeout, unit);
    }

    /** Done with a received message (handled or discarded): removes it from the processing list. */
    public void acknowledge(String raw) {
        processing.remove(raw);
    }

    /**
     * Puts a message back on the queue for another attempt, then acknowledges the
     * original. In this order, a crash in between gives a duplicate, not a loss.
     */
    public void retry(String raw, PriceChange next) {
        queue.addFirst(next.toJson());
        acknowledge(raw);
    }

    /** Moves a message that keeps failing to the dead-letter list. */
    public void deadLetter(String raw) {
        dead.add(raw);
        acknowledge(raw);
    }

    /**
     * Moves every message left in the processing list (by a consumer that
     * stopped without acknowledging) back onto the queue. Call it before
     * consuming starts. Returns how many were moved.
     */
    public int recover() {
        int moved = 0;
        // RPOPLPUSH processing -> queue, one message at a time, until it is empty.
        while (processing.pollLastAndOfferFirstTo(queue.getName()) != null) {
            moved++;
        }
        return moved;
    }

    /** Messages waiting. */
    public int size() {
        return queue.size();
    }

    /** Messages received but not yet acknowledged. */
    public int processingSize() {
        return processing.size();
    }

    public List<String> deadLetters() {
        return dead.readAll();
    }
}
