package com.example.core.product;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One catalog product: an immutable value object.
 * <p>
 * This replaces the CMP entity bean. The bean had container-managed fields that
 * were written to the database at commit. Products are now plain values, so a
 * change ({@link #withDiscount}) gives a new {@code Product}, which
 * {@link ProductCatalog} stores. It is stored in Redis as JSON, which is why it
 * has a private no-argument constructor and no {@code final} fields.
 */
public final class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sku;
    private String name;
    private BigDecimal price;

    /** For the JSON codec only. */
    private Product() {
    }

    private Product(String sku, String name, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    /**
     * Validates and normalizes a new product: the SKU is trimmed and upper-cased,
     * the name trimmed, and the price rounded to two decimals.
     *
     * @throws IllegalArgumentException for a missing SKU or name, or a missing or negative price
     */
    public static Product of(String sku, String name, BigDecimal price) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or positive");
        }
        return new Product(normalizeSku(sku), name.trim(), price.setScale(2, RoundingMode.HALF_EVEN));
    }

    /** The form SKUs are stored in: trimmed and upper case. {@code null} stays {@code null}. */
    public static String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase();
    }

    /**
     * The same product with its price reduced by {@code percent}.
     *
     * @throws IllegalArgumentException unless the percent is between 1 and 99
     */
    public Product withDiscount(int percent) {
        if (percent <= 0 || percent >= 100) {
            throw new IllegalArgumentException("Discount must be between 1 and 99 percent");
        }
        BigDecimal factor = BigDecimal.valueOf(100 - percent).divide(BigDecimal.valueOf(100));
        return new Product(sku, name, price.multiply(factor).setScale(2, RoundingMode.HALF_EVEN));
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product)) {
            return false;
        }
        Product other = (Product) o;
        return Objects.equals(sku, other.sku) && Objects.equals(name, other.name)
                && Objects.equals(price, other.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, name, price);
    }

    @Override
    public String toString() {
        return "Product[" + sku + ", " + name + ", " + price + "]";
    }
}
