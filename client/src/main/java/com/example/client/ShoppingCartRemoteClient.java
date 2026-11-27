package com.example.client;

import com.example.core.cart.CartNotFoundException;
import com.example.core.cart.ItemNotFoundException;
import com.example.core.cart.ShoppingCartService;
import org.redisson.api.RedissonClient;

/**
 * Standalone client for the shopping cart, calling the web application's
 * {@link ShoppingCartService} through Redisson RemoteService.
 * <p>
 * It needs only the Redis address ({@code -Dredis.address=...} or
 * {@code REDIS_ADDRESS}, default {@code redis://localhost:6379}) and the web
 * application running against the same Redis. There is no JNDI setup, server
 * client library or Java 8 requirement.
 * <p>
 * Argument (optional): the customer name, default {@code Alice}.
 */
public final class ShoppingCartRemoteClient {

    private ShoppingCartRemoteClient() {
    }

    public static void main(String[] args) throws Exception {
        String customer = args.length > 0 ? args[0] : "Alice";
        RedissonClient redisson = RemoteServices.connect();
        try {
            run(RemoteServices.proxy(redisson, ShoppingCartService.class), customer);
        } finally {
            redisson.shutdown();
        }
    }

    static void run(ShoppingCartService carts, String customer) throws Exception {
        // createCart replaces home.create(customer). It returns an id rather
        // than a stub; the cart's state lives in Redis.
        String cartId = carts.createCart(customer);
        System.out.println("Created cart for " + carts.getCustomerName(cartId));

        // Every call names the same cart, so state builds up between calls.
        carts.addItem(cartId, "Java EE 6 Tutorial");
        carts.addItem(cartId, "Coffee mug");
        carts.addItem(cartId, "Keyboard");
        System.out.println("Items after adding: " + carts.getItems(cartId));

        carts.removeItem(cartId, "Coffee mug");
        System.out.println("Items after removing: " + carts.getItems(cartId));

        // A checked exception thrown by the service reaches the client as itself.
        try {
            carts.removeItem(cartId, "Unicorn");
        } catch (ItemNotFoundException e) {
            System.out.println("Expected application exception: " + e.getMessage());
        }

        // The id is what an EJB Handle used to be: a serializable reference to
        // the conversation. Any client, or a later run, can continue with it.
        System.out.println("Cart id " + cartId + " holds " + carts.getItemCount(cartId) + " item(s)");

        // removeCart ends the conversation and deletes the state.
        carts.removeCart(cartId);
        System.out.println("Cart removed.");

        try {
            carts.getItemCount(cartId);
        } catch (CartNotFoundException e) {
            System.out.println("Expected after removeCart(): " + e.getClass().getSimpleName());
        }
    }
}
