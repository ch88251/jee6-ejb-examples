package com.example.core.product;

/** Thrown when no product has the given SKU. */
public class ProductNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(String sku) {
        super("No product with SKU " + sku);
    }
}
