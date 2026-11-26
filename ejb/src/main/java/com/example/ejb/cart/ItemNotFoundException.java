package com.example.ejb.cart;

/** Application exception thrown when removing an item that is not in the cart. */
public class ItemNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public ItemNotFoundException(String item) {
        super("Item not in cart: " + item);
    }
}
