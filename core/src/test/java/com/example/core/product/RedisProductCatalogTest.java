package com.example.core.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.core.testsupport.RedisTestServer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/** Tests {@link RedisProductCatalog} against a real Redis (Docker). */
class RedisProductCatalogTest {

    private RedissonClient redisson;
    private RedisProductCatalog catalog;

    @BeforeEach
    void setUp() {
        redisson = RedisTestServer.newEmptyClient();
        catalog = new RedisProductCatalog(redisson, "test:");
    }

    @AfterEach
    void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void createsAndFindsNormalizedProducts() throws Exception {
        Product created = catalog.create(" mg-002 ", " Coffee mug ", new BigDecimal("9.5"));

        assertEquals(Product.of("MG-002", "Coffee mug", new BigDecimal("9.50")), created);
        assertEquals(Optional.of(created), catalog.find("mg-002"));
        assertEquals(Optional.empty(), catalog.find("nope"));
        assertEquals(Optional.empty(), catalog.find(null));
    }

    @Test
    void productsAreStoredAsReadableJson() throws Exception {
        catalog.create("MG-002", "Coffee mug", new BigDecimal("9.50"));

        String json = redisson.<String, String>getMap("test:products", StringCodec.INSTANCE).get("\"MG-002\"");
        // Redisson's JSON mapper writes the properties in alphabetical order.
        assertEquals("{\"name\":\"Coffee mug\",\"price\":9.50,\"sku\":\"MG-002\"}", json);
    }

    @Test
    void duplicateSkusAreRejected() throws Exception {
        catalog.create("MG-002", "Coffee mug", BigDecimal.ONE);

        DuplicateProductException e = assertThrows(DuplicateProductException.class,
                () -> catalog.create("mg-002", "Other", BigDecimal.TEN));
        assertEquals("A product with SKU MG-002 already exists.", e.getMessage());
        assertEquals("Coffee mug", catalog.find("MG-002").get().getName());
    }

    @Test
    void invalidProductsAreRejectedBeforeStoring() {
        assertThrows(IllegalArgumentException.class, () -> catalog.create("S", "n", new BigDecimal("-1")));
        assertTrue(catalog.findAll().isEmpty());
    }

    @Test
    void findAllIsSortedBySkuAndFindByMaxPriceByPrice() throws Exception {
        loadSamples();

        assertEquals(List.of("BK-001", "HD-004", "KB-003", "MG-002"), skus(catalog.findAll()));
        assertEquals(List.of("MG-002", "BK-001"), skus(catalog.findByMaxPrice(new BigDecimal("39.99"))));
        assertTrue(catalog.findByMaxPrice(new BigDecimal("1")).isEmpty());
        assertThrows(NullPointerException.class, () -> catalog.findByMaxPrice(null));
    }

    @Test
    void averagePrice() throws Exception {
        assertEquals(new BigDecimal("0.00"), catalog.getAveragePrice());
        loadSamples();
        // (39.99 + 9.50 + 129.00 + 249.00) / 4 = 106.8725
        assertEquals(new BigDecimal("106.87"), catalog.getAveragePrice());
    }

    @Test
    void appliesADiscount() throws Exception {
        catalog.create("MG-002", "Coffee mug", new BigDecimal("9.50"));

        Product updated = catalog.applyDiscount(" mg-002", 10);

        assertEquals(new BigDecimal("8.55"), updated.getPrice());
        assertEquals(new BigDecimal("8.55"), catalog.find("MG-002").get().getPrice());
    }

    @Test
    void discountErrors() throws Exception {
        catalog.create("MG-002", "Coffee mug", new BigDecimal("9.50"));

        assertThrows(ProductNotFoundException.class, () -> catalog.applyDiscount("NOPE", 10));
        assertThrows(ProductNotFoundException.class, () -> catalog.applyDiscount(null, 10));
        assertThrows(IllegalArgumentException.class, () -> catalog.applyDiscount("MG-002", 0));
        assertEquals(new BigDecimal("9.50"), catalog.find("MG-002").get().getPrice());
    }

    @Test
    void concurrentDiscountsAllApply() throws Exception {
        catalog.create("KB-003", "Keyboard", new BigDecimal("1000.00"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Product>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tasks.add(() -> catalog.applyDiscount("KB-003", 1));
            }
            for (Future<Product> f : pool.invokeAll(tasks)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        // The same 20 discounts applied one after another.
        Product expected = Product.of("KB-003", "Keyboard", new BigDecimal("1000.00"));
        for (int i = 0; i < 20; i++) {
            expected = expected.withDiscount(1);
        }
        assertEquals(expected.getPrice(), catalog.find("KB-003").get().getPrice());
    }

    @Test
    void deletes() throws Exception {
        catalog.create("MG-002", "Coffee mug", BigDecimal.ONE);

        assertTrue(catalog.delete("mg-002"));
        assertFalse(catalog.delete("MG-002"));
        assertFalse(catalog.delete(null));
        assertTrue(catalog.findAll().isEmpty());
    }

    private void loadSamples() throws Exception {
        catalog.create("BK-001", "Java EE 6 Tutorial", new BigDecimal("39.99"));
        catalog.create("MG-002", "Coffee mug", new BigDecimal("9.50"));
        catalog.create("KB-003", "Mechanical keyboard", new BigDecimal("129.00"));
        catalog.create("HD-004", "Noise-cancelling headphones", new BigDecimal("249.00"));
    }

    private static List<String> skus(List<Product> products) {
        return products.stream().map(Product::getSku).collect(Collectors.toList());
    }
}
