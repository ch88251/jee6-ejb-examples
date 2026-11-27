package com.example.core.product;

/** Thrown when creating a product whose SKU already exists. */
public class DuplicateProductException extends Exception {

    private static final long serialVersionUID = 1L;

    public DuplicateProductException(String sku) {
        super("A product with SKU " + sku + " already exists.");
    }
}
