package com.example.core.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The product catalog. It replaces the CMP entity's home interface: the
 * {@code create}, {@code find...} and home business methods are all here, and
 * the {@code ejbSelect}/EJB-QL queries became plain Java.
 * <p>
 * SKUs are matched after normalizing ({@link Product#normalizeSku}). Each method
 * is atomic on its own. There is no transaction spanning several calls, as the
 * old {@code UserTransaction} gave.
 */
public interface ProductCatalog {

    /**
     * @throws IllegalArgumentException if the values are not valid (see {@link Product#of})
     */
    Product create(String sku, String name, BigDecimal price) throws DuplicateProductException;

    Optional<Product> find(String sku);

    /** All products, by SKU. */
    List<Product> findAll();

    /** Products costing at most {@code maxPrice}, cheapest first. */
    List<Product> findByMaxPrice(BigDecimal maxPrice);

    /**
     * Reduces a product's price by {@code percent} and returns the updated product.
     *
     * @throws IllegalArgumentException unless the percent is between 1 and 99
     */
    Product applyDiscount(String sku, int percent) throws ProductNotFoundException;

    /** @return {@code false} if there was no such product */
    boolean delete(String sku);

    /** The average price, or 0.00 if the catalog is empty. */
    BigDecimal getAveragePrice();
}
