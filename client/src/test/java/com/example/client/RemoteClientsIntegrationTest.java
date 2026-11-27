package com.example.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.client.testsupport.ConsoleCapture;
import com.example.core.AppServices;
import com.example.core.RedisConnection;
import com.example.core.cart.CartNotFoundException;
import com.example.core.cart.ItemNotFoundException;
import com.example.core.cart.ShoppingCartService;
import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.UnsupportedCurrencyException;
import com.example.core.testsupport.RedisTestServer;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

/**
 * The clients against the real services over Redisson RemoteService, through a
 * Redis in Docker: the server side is an {@link AppServices}, as the web
 * application runs it, and the client side a separate Redisson client.
 */
class RemoteClientsIntegrationTest {

    private RedissonClient serverRedis;
    private RedissonClient clientRedis;
    private AppServices server;

    @BeforeEach
    void setUp() {
        serverRedis = RedisTestServer.newEmptyClient();
        server = AppServices.start(serverRedis, false);
        clientRedis = RedisTestServer.newClient();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(RedisConnection.ADDRESS_PROPERTY);
        if (server != null) {
            server.close();
        }
        if (serverRedis != null) {
            serverRedis.shutdown();
        }
        if (clientRedis != null) {
            clientRedis.shutdown();
        }
    }

    @Test
    void cartConversationWorksRemotely() throws Exception {
        ShoppingCartService carts = RemoteServices.proxy(clientRedis, ShoppingCartService.class);

        String id = carts.createCart("  Remote Rita ");
        carts.addItem(id, "Book");
        carts.addItem(id, "Pen");

        assertEquals("Remote Rita", carts.getCustomerName(id));
        assertEquals(Arrays.asList("Book", "Pen"), carts.getItems(id));
        // The server's own view (not through RemoteService) sees the same cart.
        assertEquals(2, server.cartService().getItemCount(id));
    }

    @Test
    void checkedExceptionsReachTheClientAsThemselves() throws Exception {
        ShoppingCartService carts = RemoteServices.proxy(clientRedis, ShoppingCartService.class);
        CurrencyConverter converter = RemoteServices.proxy(clientRedis, CurrencyConverter.class);
        String id = carts.createCart("Ed");

        ItemNotFoundException notInCart = assertThrows(ItemNotFoundException.class, () -> carts.removeItem(id, "Ghost"));
        assertEquals("Item not in cart: Ghost", notInCart.getMessage());
        UnsupportedCurrencyException unsupported = assertThrows(UnsupportedCurrencyException.class,
                () -> converter.convert(BigDecimal.ONE, "USD", "XYZ"));
        assertEquals("Unsupported currency: XYZ", unsupported.getMessage());

        carts.removeCart(id);
        assertThrows(CartNotFoundException.class, () -> carts.getItemCount(id));
    }

    @Test
    void runtimeExceptionsReachTheClient() {
        ShoppingCartService carts = RemoteServices.proxy(clientRedis, ShoppingCartService.class);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> carts.createCart(" "));
        assertEquals("A customer name is required", e.getMessage());
    }

    @Test
    void converterReadsTheCommissionFromRedisOnEveryCall() throws Exception {
        CurrencyConverter converter = RemoteServices.proxy(clientRedis, CurrencyConverter.class);
        assertEquals(new BigDecimal("1.5"), converter.getCommissionPercent());

        server.config().set("commissionPercent", "0");

        assertEquals(new BigDecimal("92.00"), converter.convert(new BigDecimal("100"), "USD", "EUR"));
    }

    @Test
    void cartClientMainRunsTheWholeConversation() throws Exception {
        System.setProperty(RedisConnection.ADDRESS_PROPERTY, RedisTestServer.address());
        String out;
        try (ConsoleCapture console = new ConsoleCapture()) {
            ShoppingCartRemoteClient.main(new String[] {"Zoe"});
            ShoppingCartRemoteClient.main(new String[0]);
            out = console.output();
        }
        assertTrue(out.contains("Created cart for Zoe\n"), out);
        assertTrue(out.contains("Created cart for Alice\n"), out);   // the default customer
        assertTrue(out.contains("Items after removing: [Java EE 6 Tutorial, Keyboard]\n"), out);
        assertTrue(out.contains("Expected application exception: Item not in cart: Unicorn\n"), out);
        assertTrue(out.contains("Expected after removeCart(): CartNotFoundException\n"), out);
    }

    @Test
    void converterClientMainRunsTheSampleConversions() throws Exception {
        System.setProperty(RedisConnection.ADDRESS_PROPERTY, RedisTestServer.address());
        String out;
        try (ConsoleCapture console = new ConsoleCapture()) {
            CurrencyConverterRemoteClient.main(new String[] {"250", "GBP", "JPY"});
            out = console.output();
        }
        assertTrue(out.contains("Supported currencies: [USD, EUR, GBP, JPY, CAD]\n"), out);
        assertTrue(out.contains("Commission: 1.5%\n"), out);
        assertTrue(out.contains("250 GBP = 47161.55 JPY\n"), out);
        assertTrue(out.contains("Expected application exception: Unsupported currency: XYZ\n"), out);
    }
}
