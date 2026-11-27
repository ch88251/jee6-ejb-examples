package com.example.core.cart;

import java.util.List;

/**
 * A shopping cart that keeps per-customer state between calls.
 * <p>
 * This used to be a stateful session bean, where the container tied each
 * client to its own bean instance. Here the state lives in Redis instead, and
 * every call names the cart by its id. The service itself is stateless and
 * thread-safe, so one instance serves every client, and any server (or a
 * remote client, through Redisson RemoteService) can continue a conversation
 * given the id. The id plays the part of the old EJB {@code Handle}.
 */
public interface ShoppingCartService {

    /**
     * Starts a new cart and returns its id.
     *
     * @throws IllegalArgumentException if the customer name is empty
     */
    String createCart(String customerName);

    String getCustomerName(String cartId) throws CartNotFoundException;

    /** @throws IllegalArgumentException if the item is empty */
    void addItem(String cartId, String item) throws CartNotFoundException;

    void removeItem(String cartId, String item) throws CartNotFoundException, ItemNotFoundException;

    /** A copy of the items, in the order they were added. */
    List<String> getItems(String cartId) throws CartNotFoundException;

    int getItemCount(String cartId) throws CartNotFoundException;

    /**
     * Ends the conversation and deletes the cart's state.
     *
     * @return {@code false} if there was no such cart
     */
    boolean removeCart(String cartId);
}
