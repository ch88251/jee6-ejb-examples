package com.example.core.pricing;

/** Where producers (the pricing page) send price changes. Processing happens later, elsewhere. */
public interface PriceChangeQueue {

    /** Queues a discount of {@code percent} for {@code sku} and returns the message id. */
    String send(String sku, int percent);
}
