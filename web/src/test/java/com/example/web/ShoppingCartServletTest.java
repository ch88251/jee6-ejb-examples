package com.example.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.core.cart.CartNotFoundException;
import com.example.core.cart.ItemNotFoundException;
import com.example.core.cart.ShoppingCartService;
import java.util.Arrays;
import java.util.Collections;
import javax.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ShoppingCartServlet}: the cart id kept in the HTTP
 * session, and each action's call on the (mocked) cart service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // cart getters are stubbed for every render
class ShoppingCartServletTest {

    private static final String ID = "cart-1";

    @Mock private ShoppingCartService carts;

    private ShoppingCartServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        servlet = ServletSetup.init(new ShoppingCartServlet(), ShoppingCartService.class, carts);
        http = new HttpFixture();
        when(carts.createCart(anyString())).thenReturn(ID);
        when(carts.getCustomerName(ID)).thenReturn("Bob");
        when(carts.getItems(ID)).thenReturn(Collections.<String>emptyList());
    }

    @Test
    void initFailsWithoutTheService() {
        assertThrows(ServletException.class,
                () -> ServletSetup.init(new ShoppingCartServlet(), String.class, "unrelated"));
    }

    @Test
    void getWithoutCartShowsStartForm() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("name='customer'"));
        verifyNoInteractions(carts);
    }

    @Test
    void loginCreatesACartAndKeepsOnlyItsIdInTheSession() throws Exception {
        servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response);

        verify(carts).createCart("Bob");
        assertEquals(ID, http.sessionAttributes.get(ShoppingCartServlet.CART_ID_ATTR));
        assertTrue(http.html().contains("New cart created for Bob"));
        assertTrue(http.html().contains("Cart for <b>Bob</b> (0 item(s))"));
    }

    @Test
    void loginAgainRemovesThePreviousCart() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, "old-cart");
        when(carts.createCart("Ann")).thenReturn("new-cart");
        when(carts.getCustomerName("new-cart")).thenReturn("Ann");
        when(carts.getItems("new-cart")).thenReturn(Collections.<String>emptyList());

        servlet.doPost(http.param("action", "login").param("customer", "Ann").request, http.response);

        verify(carts).removeCart("old-cart");
        assertEquals("new-cart", http.sessionAttributes.get(ShoppingCartServlet.CART_ID_ATTR));
    }

    @Test
    void invalidCustomerNameIsShownToTheUser() throws Exception {
        when(carts.createCart(" ")).thenThrow(new IllegalArgumentException("A customer name is required"));

        servlet.doPost(http.param("action", "login").param("customer", " ").request, http.response);

        assertTrue(http.html().contains("A customer name is required"));
        assertNull(http.sessionAttributes.get(ShoppingCartServlet.CART_ID_ATTR));
    }

    @Test
    void actionsWithoutCartAskToStartOne() throws Exception {
        servlet.doPost(http.param("action", "add").param("item", "x").request, http.response);

        assertTrue(http.html().contains("Please start a cart first."));
        verify(carts, never()).addItem(anyString(), anyString());
    }

    @Test
    void addAndRemoveGoToTheSameCartAcrossRequests() throws Exception {
        servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response);
        HttpFixture second = http.nextRequest().param("action", "add").param("item", "Book");
        servlet.doPost(second.request, second.response);
        HttpFixture third = http.nextRequest().param("action", "remove").param("item", "Book");
        servlet.doPost(third.request, third.response);

        verify(carts).addItem(ID, "Book");
        verify(carts).removeItem(ID, "Book");
        assertTrue(second.html().contains("Item added."));
        assertTrue(third.html().contains("Item removed."));
    }

    @Test
    void emptyItemIsShownToTheUser() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        doThrow(new IllegalArgumentException("Item must not be empty")).when(carts).addItem(ID, "");

        servlet.doPost(http.param("action", "add").param("item", "").request, http.response);

        assertTrue(http.html().contains("Item must not be empty"));
    }

    @Test
    void cartContentsAreRendered() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        when(carts.getItems(ID)).thenReturn(Arrays.asList("Book", "Mug"));

        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("Cart for <b>Bob</b> (2 item(s))"));
        assertTrue(http.html().contains("<li>Book"));
        assertTrue(http.html().contains("<li>Mug"));
        assertTrue(http.html().contains("<code>cart-1</code>"));
    }

    @Test
    void removingUnknownItemShowsApplicationException() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        doThrow(new ItemNotFoundException("Ghost")).when(carts).removeItem(ID, "Ghost");

        servlet.doPost(http.param("action", "remove").param("item", "Ghost").request, http.response);

        assertTrue(http.html().contains("Item not in cart: Ghost"));
    }

    @Test
    void checkoutRemovesTheCartAndClearsTheSession() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        when(carts.getItemCount(ID)).thenReturn(3);

        servlet.doPost(http.param("action", "checkout").request, http.response);

        verify(carts).removeCart(ID);
        assertFalse(http.sessionAttributes.containsKey(ShoppingCartServlet.CART_ID_ATTR));
        assertTrue(http.html().contains("Checked out 3 item(s) for Bob."));
        assertTrue(http.html().contains("name='customer'"));
    }

    @Test
    void expiredCartIsDroppedFromTheSession() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        doThrow(new CartNotFoundException(ID)).when(carts).addItem(ID, "Book");

        servlet.doPost(http.param("action", "add").param("item", "Book").request, http.response);

        assertFalse(http.sessionAttributes.containsKey(ShoppingCartServlet.CART_ID_ATTR));
        assertTrue(http.html().contains("Your cart has expired, please start again."));
        assertTrue(http.html().contains("name='customer'"));
    }

    @Test
    void cartExpiringBeforeRenderingIsDroppedToo() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        when(carts.getCustomerName(ID)).thenThrow(new CartNotFoundException(ID));

        servlet.doGet(http.request, http.response);

        assertFalse(http.sessionAttributes.containsKey(ShoppingCartServlet.CART_ID_ATTR));
        assertTrue(http.html().contains("Your cart has expired"));
    }

    @Test
    void userInputIsHtmlEscaped() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);
        when(carts.getCustomerName(ID)).thenReturn("<b>Eve</b>");
        when(carts.getItems(ID)).thenReturn(Collections.singletonList("<script>x</script>"));

        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("&lt;b&gt;Eve&lt;/b&gt;"));
        assertTrue(http.html().contains("&lt;script&gt;x&lt;/script&gt;"));
        assertFalse(http.html().contains("<script>"));
    }

    @Test
    void unknownActionChangesNothing() throws Exception {
        http.sessionAttributes.put(ShoppingCartServlet.CART_ID_ATTR, ID);

        servlet.doPost(http.param("action", "dance").request, http.response);

        verify(carts, never()).addItem(anyString(), anyString());
        verify(carts, never()).removeCart(anyString());
        assertTrue(http.html().contains("Cart for <b>Bob</b>"));
    }
}
