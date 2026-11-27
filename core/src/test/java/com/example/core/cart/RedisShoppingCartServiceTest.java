package com.example.core.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.core.testsupport.RedisTestServer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/** Tests {@link RedisShoppingCartService} against a real Redis (Docker). */
class RedisShoppingCartServiceTest {

    private static final String PREFIX = "test:";

    private RedissonClient redisson;
    private RedisShoppingCartService carts;

    @BeforeEach
    void setUp() {
        redisson = RedisTestServer.newEmptyClient();
        carts = new RedisShoppingCartService(redisson, PREFIX, Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void stateBuildsUpAcrossCalls() throws Exception {
        String id = carts.createCart("  Alice ");
        carts.addItem(id, " Book ");
        carts.addItem(id, "Mug");
        carts.addItem(id, "Book");

        assertEquals("Alice", carts.getCustomerName(id));
        assertEquals(Arrays.asList("Book", "Mug", "Book"), carts.getItems(id));
        assertEquals(3, carts.getItemCount(id));
    }

    @Test
    void stateIsStoredAsPlainRedisKeys() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");

        assertEquals("Alice", redisson.getBucket("test:cart:{" + id + "}:customer", StringCodec.INSTANCE).get());
        assertEquals(Collections.singletonList("Book"),
                redisson.getList("test:cart:{" + id + "}:items", StringCodec.INSTANCE).readAll());
    }

    @Test
    void eachCartIsSeparate() throws Exception {
        String a = carts.createCart("A");
        String b = carts.createCart("B");
        carts.addItem(a, "x");

        assertNotEquals(a, b);
        assertEquals(0, carts.getItemCount(b));
        assertEquals("B", carts.getCustomerName(b));
    }

    @Test
    void anotherServiceInstanceSeesTheSameCart() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");

        RedisShoppingCartService otherServer = new RedisShoppingCartService(redisson, PREFIX, Duration.ofMinutes(5));
        assertEquals(Collections.singletonList("Book"), otherServer.getItems(id));
    }

    @Test
    void removeItemRemovesOnlyTheFirstMatch() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");
        carts.addItem(id, "Mug");
        carts.addItem(id, "Book");

        carts.removeItem(id, "Book");

        assertEquals(Arrays.asList("Mug", "Book"), carts.getItems(id));
    }

    @Test
    void removingAMissingItemIsAnApplicationException() throws Exception {
        String id = carts.createCart("Alice");

        assertEquals("Item not in cart: Ghost",
                assertThrows(ItemNotFoundException.class, () -> carts.removeItem(id, "Ghost")).getMessage());
        assertThrows(ItemNotFoundException.class, () -> carts.removeItem(id, null));
    }

    @Test
    void itemsAreAReadOnlyCopy() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");
        List<String> items = carts.getItems(id);

        assertThrows(UnsupportedOperationException.class, () -> items.add("x"));
        carts.addItem(id, "Mug");
        assertEquals(1, items.size());
    }

    @Test
    void validatesInput() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> carts.createCart(null));
        assertThrows(IllegalArgumentException.class, () -> carts.createCart("  "));
        String id = carts.createCart("Alice");
        assertThrows(IllegalArgumentException.class, () -> carts.addItem(id, " "));
        assertThrows(IllegalArgumentException.class, () -> carts.addItem(id, null));
    }

    @Test
    void removeCartEndsTheConversation() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");

        assertTrue(carts.removeCart(id));

        assertThrows(CartNotFoundException.class, () -> carts.getItemCount(id));
        assertThrows(CartNotFoundException.class, () -> carts.addItem(id, "x"));
        assertEquals(0, redisson.getKeys().count());
        assertFalse(carts.removeCart(id));
        assertFalse(carts.removeCart(null));
    }

    @Test
    void unknownAndNullIdsAreNotFound() {
        assertThrows(CartNotFoundException.class, () -> carts.getCustomerName("no-such-cart"));
        assertThrows(CartNotFoundException.class, () -> carts.getItems(null));
    }

    @Test
    void everyCallPushesBackTheExpiry() throws Exception {
        String id = carts.createCart("Alice");
        carts.addItem(id, "Book");
        String customerKey = "test:cart:{" + id + "}:customer";
        String itemsKey = "test:cart:{" + id + "}:items";
        redisson.getBucket(customerKey).expire(Duration.ofSeconds(10));
        redisson.getBucket(itemsKey).expire(Duration.ofSeconds(10));

        carts.getItemCount(id);

        assertTrue(redisson.getBucket(customerKey).remainTimeToLive() > 60_000);
        assertTrue(redisson.getBucket(itemsKey).remainTimeToLive() > 60_000);
    }

    @Test
    void idleCartsExpire() throws Exception {
        RedisShoppingCartService shortLived = new RedisShoppingCartService(redisson, PREFIX, Duration.ofMillis(300));
        String id = shortLived.createCart("Alice");
        shortLived.addItem(id, "Book");

        Thread.sleep(800);

        assertThrows(CartNotFoundException.class, () -> shortLived.getItems(id));
        assertEquals(0, redisson.getKeys().count());
    }

    @Test
    void requiresItsDependencies() {
        assertThrows(NullPointerException.class, () -> new RedisShoppingCartService(null, PREFIX, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new RedisShoppingCartService(redisson, null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new RedisShoppingCartService(redisson, PREFIX, null));
    }
}
