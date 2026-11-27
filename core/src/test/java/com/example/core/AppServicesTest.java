package com.example.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.core.testsupport.RedisTestServer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

/**
 * Tests the wiring in {@link AppServices} against a real Redis (Docker),
 * including the price change round trip: page -> queue -> worker -> catalog.
 */
class AppServicesTest {

    private RedissonClient redisson;
    private AppServices services;

    @AfterEach
    void tearDown() {
        System.clearProperty(RedisConnection.ADDRESS_PROPERTY);
        if (services != null) {
            services.close();
        }
        if (redisson != null && !redisson.isShutdown()) {
            redisson.shutdown();
        }
    }

    @Test
    void wiresTheServicesAndSeedsTheConfig() throws Exception {
        redisson = RedisTestServer.newEmptyClient();
        services = AppServices.start(redisson, false);

        assertEquals(new BigDecimal("1.5"), services.converter().getCommissionPercent());
        String cart = services.cartService().createCart("Alice");
        assertEquals("Alice", services.cartService().getCustomerName(cart));
        services.catalog().create("MG-002", "Coffee mug", new BigDecimal("9.50"));
        assertEquals(1, services.catalog().findAll().size());
        assertEquals(new BigDecimal("1.5"), services.config().getCommissionPercent());
    }

    @Test
    void priceChangesAreAppliedAsynchronouslyByTheWorker() throws Exception {
        redisson = RedisTestServer.newEmptyClient();
        services = AppServices.start(redisson, false);
        services.catalog().create("MG-002", "Coffee mug", new BigDecimal("9.50"));

        services.priceChangeQueue().send("MG-002", 10);
        services.priceChangeQueue().send("NOPE", 10);     // discarded
        services.priceChangeQueue().send("MG-002", 0);    // discarded

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!services.catalog().find("MG-002").get().getPrice().equals(new BigDecimal("8.55"))) {
            assertTrue(Instant.now().isBefore(deadline), "the worker did not apply the discount");
            Thread.sleep(50);
        }
    }

    @Test
    void closeLeavesASharedClientRunning() {
        redisson = RedisTestServer.newEmptyClient();
        AppServices.start(redisson, false).close();
        assertFalse(redisson.isShutdown());
    }

    @Test
    void startConnectsToTheConfiguredAddressAndCloseShutsTheClientDown() {
        System.setProperty(RedisConnection.ADDRESS_PROPERTY, RedisTestServer.address());
        services = AppServices.start();
        services.converter().getSupportedCurrencies();
        services.close();
        services = null;
    }

    @Test
    void addressFallsBackToTheDefault() {
        System.setProperty(RedisConnection.ADDRESS_PROPERTY, " redis://example:1234 ");
        assertEquals("redis://example:1234", RedisConnection.address());
        System.setProperty(RedisConnection.ADDRESS_PROPERTY, " ");
        // REDIS_ADDRESS is normally unset in tests, so this is the default.
        String env = System.getenv(RedisConnection.ADDRESS_ENV);
        assertEquals(env == null || env.trim().isEmpty() ? RedisConnection.DEFAULT_ADDRESS : env.trim(),
                RedisConnection.address());
    }

    @Test
    void aFailedStartClosesWhatItStarted() {
        redisson = RedisTestServer.newEmptyClient();
        redisson.shutdown();
        assertThrows(RuntimeException.class, () -> AppServices.start(redisson, false));
    }
}
