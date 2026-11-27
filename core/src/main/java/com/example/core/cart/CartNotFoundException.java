package com.example.core.cart;

/**
 * Thrown when a cart id doesn't name a live cart: it was checked out, or it
 * expired after being idle for longer than the cart timeout. This replaces the
 * EJB {@code NoSuchObjectLocalException} of a removed or timed-out stateful bean.
 */
public class CartNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public CartNotFoundException(String cartId) {
        super("No such cart (checked out or expired): " + cartId);
    }
}
