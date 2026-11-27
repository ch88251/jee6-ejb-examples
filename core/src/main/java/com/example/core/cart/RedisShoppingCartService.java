package com.example.core.cart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * {@link ShoppingCartService} that keeps each cart in two Redis keys:
 * <ul>
 *   <li>{@code <prefix>cart:{id}:customer}: a string with the customer name. Its
 *       presence is what makes the cart exist.</li>
 *   <li>{@code <prefix>cart:{id}:items}: a list of item names.</li>
 * </ul>
 * Both keys expire after {@code timeout} without use, which replaces the
 * stateful bean's timeout. Every call pushes the expiry back. Because the state
 * is in Redis rather than in a bean instance, there is no passivation or
 * activation, and carts survive a server restart.
 */
public class RedisShoppingCartService implements ShoppingCartService {

    private final RedissonClient redisson;
    private final String keyPrefix;
    private final Duration timeout;

    public RedisShoppingCartService(RedissonClient redisson, String keyPrefix, Duration timeout) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public String createCart(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new IllegalArgumentException("A customer name is required");
        }
        String cartId = UUID.randomUUID().toString();
        customer(cartId).set(customerName.trim(), timeout);
        log("created cart " + cartId + " for " + customerName.trim());
        return cartId;
    }

    @Override
    public String getCustomerName(String cartId) throws CartNotFoundException {
        return touch(cartId);
    }

    @Override
    public void addItem(String cartId, String item) throws CartNotFoundException {
        if (item == null || item.trim().isEmpty()) {
            throw new IllegalArgumentException("Item must not be empty");
        }
        touch(cartId);
        RList<String> items = items(cartId);
        items.add(item.trim());
        items.expire(timeout);
    }

    @Override
    public void removeItem(String cartId, String item) throws CartNotFoundException, ItemNotFoundException {
        touch(cartId);
        // LREM count 1: removes the first occurrence, like ArrayList.remove(Object).
        if (item == null || !items(cartId).remove(item)) {
            throw new ItemNotFoundException(item);
        }
    }

    @Override
    public List<String> getItems(String cartId) throws CartNotFoundException {
        touch(cartId);
        return Collections.unmodifiableList(new ArrayList<>(items(cartId).readAll()));
    }

    @Override
    public int getItemCount(String cartId) throws CartNotFoundException {
        touch(cartId);
        return items(cartId).size();
    }

    @Override
    public boolean removeCart(String cartId) {
        if (cartId == null) {
            return false;
        }
        boolean removed = redisson.getKeys().delete(customerKey(cartId), itemsKey(cartId)) > 0;
        log((removed ? "removed cart " : "no cart to remove: ") + cartId);
        return removed;
    }

    /** Checks that the cart exists, pushes back its expiry, and returns the customer name. */
    private String touch(String cartId) throws CartNotFoundException {
        if (cartId == null) {
            throw new CartNotFoundException(null);
        }
        RBucket<String> customer = customer(cartId);
        String name = customer.get();
        if (name == null) {
            throw new CartNotFoundException(cartId);
        }
        customer.expire(timeout);
        items(cartId).expire(timeout);
        return name;
    }

    private RBucket<String> customer(String cartId) {
        return redisson.getBucket(customerKey(cartId), StringCodec.INSTANCE);
    }

    private RList<String> items(String cartId) {
        return redisson.getList(itemsKey(cartId), StringCodec.INSTANCE);
    }

    // The {braces} make both keys of a cart hash to the same Redis Cluster slot.
    private String customerKey(String cartId) {
        return keyPrefix + "cart:{" + cartId + "}:customer";
    }

    private String itemsKey(String cartId) {
        return keyPrefix + "cart:{" + cartId + "}:items";
    }

    private static void log(String event) {
        System.out.println("[ShoppingCartService] " + event);
    }
}
