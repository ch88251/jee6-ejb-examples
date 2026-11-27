package com.example.core.pricing;

import com.example.core.product.Product;
import com.example.core.product.ProductCatalog;
import com.example.core.product.ProductNotFoundException;
import java.util.Objects;

/**
 * Applies one price change to the catalog and decides what happens to the
 * message. This is the logic of the old message-driven bean's {@code onMessage}.
 * <ul>
 *   <li>{@link Outcome#APPLIED}: the discount was applied.</li>
 *   <li>{@link Outcome#DISCARDED}: the message can never succeed (unknown SKU,
 *       invalid percent). It is logged and dropped, because retrying would only
 *       fail again. The MDB did the same by committing instead of rolling back.</li>
 *   <li>{@link Outcome#RETRY}: the failure may be temporary (for example Redis
 *       unreachable), so the message should be delivered again. The MDB did this
 *       with {@code setRollbackOnly()}.</li>
 * </ul>
 */
public class PriceChangeHandler {

    public enum Outcome { APPLIED, DISCARDED, RETRY }

    private final ProductCatalog catalog;

    public PriceChangeHandler(ProductCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public Outcome handle(PriceChange message) {
        log("Received " + message.getId() + " sku=" + message.getSku() + " percent=" + message.getPercent()
                + (message.getAttempt() > 0 ? " (redelivery " + message.getAttempt() + ")" : ""));
        if (message.getPercent() <= 0 || message.getPercent() >= 100) {
            log("Ignoring invalid discount " + message.getPercent() + "% for " + message.getSku());
            return Outcome.DISCARDED;
        }
        try {
            Product product = catalog.applyDiscount(message.getSku(), message.getPercent());
            log("New price for " + product.getSku() + ": " + product.getPrice());
            return Outcome.APPLIED;
        } catch (ProductNotFoundException e) {
            log("No product with SKU in message, discarding: " + e.getMessage());
            return Outcome.DISCARDED;
        } catch (RuntimeException e) {
            log("Update failed, will retry: " + e);
            return Outcome.RETRY;
        }
    }

    private static void log(String event) {
        System.out.println("[PriceChangeHandler] " + event);
    }
}
