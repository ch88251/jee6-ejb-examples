package com.example.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.client.testsupport.ConsoleCapture;
import com.example.core.cart.CartNotFoundException;
import com.example.core.cart.ItemNotFoundException;
import com.example.core.cart.ShoppingCartService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ShoppingCartRemoteClient#run} with the service mocked
 * (standing in for the RemoteService proxy). {@link RemoteClientsIntegrationTest}
 * covers the real remote calls.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // removeItem is stubbed for one argument only
class ShoppingCartRemoteClientTest {

    private static final String ID = "cart-1";

    @Mock private ShoppingCartService carts;

    @Test
    void printsTheWholeConversation() throws Exception {
        when(carts.createCart("Alice")).thenReturn(ID);
        when(carts.getCustomerName(ID)).thenReturn("Alice");
        when(carts.getItems(ID)).thenReturn(
                Arrays.asList("Java EE 6 Tutorial", "Coffee mug", "Keyboard"),
                Arrays.asList("Java EE 6 Tutorial", "Keyboard"));
        doThrow(new ItemNotFoundException("Unicorn")).when(carts).removeItem(ID, "Unicorn");
        when(carts.getItemCount(ID)).thenReturn(2).thenThrow(new CartNotFoundException(ID));

        String out = run("Alice");

        assertEquals("Created cart for Alice\n"
                + "Items after adding: [Java EE 6 Tutorial, Coffee mug, Keyboard]\n"
                + "Items after removing: [Java EE 6 Tutorial, Keyboard]\n"
                + "Expected application exception: Item not in cart: Unicorn\n"
                + "Cart id cart-1 holds 2 item(s)\n"
                + "Cart removed.\n"
                + "Expected after removeCart(): CartNotFoundException\n", out);
    }

    @Test
    void everyCallNamesTheSameCartInOrder() throws Exception {
        when(carts.createCart("Bob")).thenReturn(ID);
        doThrow(new ItemNotFoundException("Unicorn")).when(carts).removeItem(ID, "Unicorn");
        when(carts.getItemCount(ID)).thenReturn(2).thenThrow(new CartNotFoundException(ID));

        run("Bob");

        InOrder order = inOrder(carts);
        order.verify(carts).createCart("Bob");
        order.verify(carts).addItem(ID, "Java EE 6 Tutorial");
        order.verify(carts).addItem(ID, "Coffee mug");
        order.verify(carts).addItem(ID, "Keyboard");
        order.verify(carts).removeItem(ID, "Coffee mug");
        order.verify(carts).removeItem(ID, "Unicorn");
        order.verify(carts).removeCart(ID);
        order.verify(carts).getItemCount(ID);
    }

    @Test
    void createFailurePropagates() {
        IllegalArgumentException failure = new IllegalArgumentException("A customer name is required");
        when(carts.createCart(" ")).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> run(" ")));
    }

    private String run(String customer) throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            ShoppingCartRemoteClient.run(carts, customer);
            return console.output();
        }
    }
}
