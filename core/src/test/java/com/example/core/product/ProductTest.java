package com.example.core.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Product} value object: validation, normalization and discounts. */
class ProductTest {

    @Test
    void normalizesSkuNameAndPrice() {
        Product p = Product.of("  bk-001 ", "  A book ", new BigDecimal("39.995"));
        assertEquals("BK-001", p.getSku());
        assertEquals("A book", p.getName());
        assertEquals(new BigDecimal("40.00"), p.getPrice());
    }

    @Test
    void rejectsInvalidValues() {
        assertEquals("SKU is required",
                assertThrows(IllegalArgumentException.class, () -> Product.of(" ", "n", BigDecimal.ONE)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Product.of(null, "n", BigDecimal.ONE));
        assertEquals("Name is required",
                assertThrows(IllegalArgumentException.class, () -> Product.of("S", "", BigDecimal.ONE)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Product.of("S", null, BigDecimal.ONE));
        assertEquals("Price must be zero or positive",
                assertThrows(IllegalArgumentException.class, () -> Product.of("S", "n", new BigDecimal("-0.01"))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Product.of("S", "n", null));
    }

    @Test
    void allowsAZeroPrice() {
        assertEquals(new BigDecimal("0.00"), Product.of("S", "Free", BigDecimal.ZERO).getPrice());
    }

    @Test
    void discountGivesANewProductAndRoundsHalfEven() {
        Product mug = Product.of("MG-002", "Coffee mug", new BigDecimal("9.50"));
        Product discounted = mug.withDiscount(15);   // 9.50 * 0.85 = 8.075 -> 8.08

        assertEquals(new BigDecimal("8.08"), discounted.getPrice());
        assertEquals("MG-002", discounted.getSku());
        assertEquals("Coffee mug", discounted.getName());
        assertEquals(new BigDecimal("9.50"), mug.getPrice());   // unchanged
    }

    @Test
    void discountMustBeBetween1And99() {
        Product p = Product.of("S", "n", BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> p.withDiscount(0));
        assertThrows(IllegalArgumentException.class, () -> p.withDiscount(100));
        assertEquals(new BigDecimal("9.90"), p.withDiscount(1).getPrice());
        assertEquals(new BigDecimal("0.10"), p.withDiscount(99).getPrice());
    }

    @Test
    void valueSemantics() {
        Product a = Product.of("S", "n", BigDecimal.ONE);
        assertEquals(a, a);
        assertEquals(a, Product.of("s", "n", new BigDecimal("1.0")));
        assertEquals(a.hashCode(), Product.of("s", "n", new BigDecimal("1.0")).hashCode());
        assertNotEquals(a, Product.of("S", "other", BigDecimal.ONE));
        assertNotEquals(a, Product.of("T", "n", BigDecimal.ONE));
        assertNotEquals(a, Product.of("S", "n", BigDecimal.TEN));
        assertNotEquals(a, "S");
        assertEquals("Product[S, n, 1.00]", a.toString());
    }

    @Test
    void normalizeSku() {
        assertEquals("AB-1", Product.normalizeSku(" ab-1 "));
        assertNull(Product.normalizeSku(null));
    }
}
