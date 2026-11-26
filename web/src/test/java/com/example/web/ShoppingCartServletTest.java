package com.example.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ejb.cart.ItemNotFoundException;
import com.example.ejb.cart.ShoppingCartLocal;
import com.example.ejb.cart.ShoppingCartLocalHome;
import com.example.ejb.testsupport.InMemoryJndi;
import java.util.Arrays;
import java.util.Collections;
import javax.ejb.CreateException;
import javax.ejb.NoSuchObjectLocalException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ShoppingCartServlet}: the stateful cart's EJBLocalObject
 * kept in the HTTP session, and each action's effect on the bean and the page.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // cart getters are stubbed for every render
class ShoppingCartServletTest {

    private static final String CART_ATTR = "shoppingCart";

    @Mock private ShoppingCartLocalHome home;
    @Mock private ShoppingCartLocal cart;
    @Mock private ServletConfig config;

    private ShoppingCartServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/ejb/ShoppingCart", home);
        servlet = new ShoppingCartServlet();
        servlet.init(config);
        http = new HttpFixture();

        when(cart.getCustomerName()).thenReturn("Bob");
        when(cart.getItems()).thenReturn(Collections.<String>emptyList());
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void getWithoutCartShowsStartForm() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("Start cart"));
        verifyNoInteractions(home);
    }

    @Test
    void loginCreatesStatefulBeanAndStoresItInSession() throws Exception {
        when(home.create("Bob")).thenReturn(cart);

        servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response);

        assertSame(cart, http.sessionAttributes.get(CART_ATTR));
        assertTrue(http.html().contains("New cart created for Bob"));
        assertTrue(http.html().contains("Cart for <b>Bob</b>"));
    }

    @Test
    void loginAgainRemovesThePreviousBean() throws Exception {
        ShoppingCartLocal oldCart = org.mockito.Mockito.mock(ShoppingCartLocal.class);
        http.sessionAttributes.put(CART_ATTR, oldCart);
        when(home.create("Bob")).thenReturn(cart);

        servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response);

        verify(oldCart).remove();
        assertSame(cart, http.sessionAttributes.get(CART_ATTR));
    }

    @Test
    void createExceptionIsShownToTheUser() throws Exception {
        when(home.create(anyString())).thenThrow(new CreateException("A customer name is required"));

        servlet.doPost(http.param("action", "login").param("customer", " ").request, http.response);

        assertTrue(http.html().contains("Could not create cart: A customer name is required"));
        assertNull(http.sessionAttributes.get(CART_ATTR));
    }

    @Test
    void missingHomeFailsTheRequest() throws Exception {
        InMemoryJndi.install(); // nothing bound

        assertThrows(ServletException.class, () ->
                servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response));
    }

    @Test
    void actionsWithoutCartAskToStartOne() throws Exception {
        servlet.doPost(http.param("action", "add").param("item", "Book").request, http.response);

        assertTrue(http.html().contains("Please start a cart first."));
    }

    @Test
    void addAndRemoveGoToTheSameBeanAcrossRequests() throws Exception {
        when(home.create("Bob")).thenReturn(cart);
        servlet.doPost(http.param("action", "login").param("customer", "Bob").request, http.response);

        HttpFixture add = http.nextRequest().param("action", "add").param("item", "Book");
        servlet.doPost(add.request, add.response);
        HttpFixture remove = http.nextRequest().param("action", "remove").param("item", "Book");
        servlet.doPost(remove.request, remove.response);

        verify(home).create("Bob"); // only once: later requests reuse the session's bean
        verify(cart).addItem("Book");
        verify(cart).removeItem("Book");
        assertTrue(add.html().contains("Item added."));
        assertTrue(remove.html().contains("Item removed."));
    }

    @Test
    void cartContentsAreRendered() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);
        when(cart.getItems()).thenReturn(Arrays.asList("Book", "Mug"));
        when(cart.getItemCount()).thenReturn(2);

        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("(2 item(s))"));
        assertTrue(http.html().contains("<li>Book"));
        assertTrue(http.html().contains("<li>Mug"));
    }

    @Test
    void removingUnknownItemShowsApplicationException() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);
        doThrow(new ItemNotFoundException("Unicorn")).when(cart).removeItem("Unicorn");

        servlet.doPost(http.param("action", "remove").param("item", "Unicorn").request, http.response);

        assertTrue(http.html().contains("Item not in cart: Unicorn"));
    }

    @Test
    void checkoutRemovesTheBeanAndClearsTheSession() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);
        when(cart.getItemCount()).thenReturn(2);

        servlet.doPost(http.param("action", "checkout").request, http.response);

        verify(cart).remove();
        verify(http.session).removeAttribute(CART_ATTR);
        assertTrue(http.html().contains("Checked out 2 item(s) for Bob."));
        assertTrue(http.html().contains("Start cart"), "back to the start form");
    }

    @Test
    void expiredBeanIsDroppedFromSession() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);
        doThrow(new NoSuchObjectLocalException("timed out")).when(cart).addItem("Book");

        servlet.doPost(http.param("action", "add").param("item", "Book").request, http.response);

        assertFalse(http.sessionAttributes.containsKey(CART_ATTR));
        assertTrue(http.html().contains("Your cart has expired"));
    }

    @Test
    void userInputIsHtmlEscaped() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);
        when(cart.getCustomerName()).thenReturn("<script>alert(1)</script>");
        when(cart.getItems()).thenReturn(Arrays.asList("O'Brien \"mug\""));

        servlet.doGet(http.request, http.response);

        assertFalse(http.html().contains("<script>"));
        assertTrue(http.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(http.html().contains("O&#39;Brien &quot;mug&quot;"));
    }

    @Test
    void unknownActionChangesNothing() throws Exception {
        http.sessionAttributes.put(CART_ATTR, cart);

        servlet.doPost(http.param("action", "bogus").request, http.response);

        verify(cart, never()).remove();
        verify(cart, never()).addItem(anyString());
    }
}
