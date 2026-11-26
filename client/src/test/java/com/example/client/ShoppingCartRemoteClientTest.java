package com.example.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.client.testsupport.ConsoleCapture;
import com.example.client.testsupport.LocalPortableRemoteObject;
import com.example.ejb.cart.ItemNotFoundException;
import com.example.ejb.cart.ShoppingCart;
import com.example.ejb.cart.ShoppingCartHome;
import com.example.ejb.testsupport.InMemoryJndi;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.Arrays;
import javax.ejb.CreateException;
import javax.ejb.EJBObject;
import javax.ejb.Handle;
import javax.naming.NameNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ShoppingCartRemoteClient}. The remote home and
 * component interfaces are Mockito mocks standing in for RMI-IIOP stubs, bound
 * in the in-memory JNDI under the client's lookup name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // the full conversation is stubbed for every test
class ShoppingCartRemoteClientTest {

    private static final String JNDI_PROPERTY = "cart.jndi";

    @Mock private ShoppingCartHome home;
    @Mock private ShoppingCart cart;
    @Mock private ShoppingCart sameCart;
    @Mock private Handle handle;

    @BeforeAll
    static void installNarrowDelegate() {
        LocalPortableRemoteObject.install();
    }

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind(ShoppingCartRemoteClient.DEFAULT_JNDI_NAME, home);
        LocalPortableRemoteObject.reset();

        when(home.create("Alice")).thenReturn(cart);
        when(cart.getCustomerName()).thenReturn("Alice");
        when(cart.getItems()).thenReturn(
                Arrays.asList("Java EE 6 Tutorial", "Coffee mug", "Keyboard"),
                Arrays.asList("Java EE 6 Tutorial", "Keyboard"));
        doThrow(new ItemNotFoundException("Unicorn")).when(cart).removeItem("Unicorn");
        when(cart.getHandle()).thenReturn(handle);
        when(handle.getEJBObject()).thenReturn(sameCart);
        when(cart.isIdentical(sameCart)).thenReturn(true);
        when(sameCart.getItemCount()).thenReturn(2);
        when(cart.getItemCount()).thenThrow(new NoSuchObjectException("removed"));
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
        System.clearProperty(JNDI_PROPERTY);
    }

    @Test
    void runsTheWholeStatefulConversation() throws Exception {
        String output = run();

        assertEquals(String.join("\n",
                "Looking up " + ShoppingCartRemoteClient.DEFAULT_JNDI_NAME,
                "Created cart for Alice",
                "Items after adding: [Java EE 6 Tutorial, Coffee mug, Keyboard]",
                "Items after removing: [Java EE 6 Tutorial, Keyboard]",
                "Expected application exception: Item not in cart: Unicorn",
                "Re-obtained from handle, identical = true, item count = 2",
                "Cart removed.",
                "Expected after remove(): NoSuchObjectException",
                ""), output);
    }

    @Test
    void allCallsGoToTheSameBeanInOrder() throws Exception {
        run();

        InOrder order = inOrder(home, cart);
        order.verify(home).create("Alice");
        order.verify(cart).addItem("Java EE 6 Tutorial");
        order.verify(cart).addItem("Coffee mug");
        order.verify(cart).addItem("Keyboard");
        order.verify(cart).removeItem("Coffee mug");
        order.verify(cart).removeItem("Unicorn");
        order.verify(cart).getHandle();
        order.verify(cart).remove();
        order.verify(cart).getItemCount();   // after remove(): expected to fail
    }

    @Test
    void customerNameComesFromFirstArgument() throws Exception {
        when(home.create("Bob")).thenReturn(cart);

        run("Bob");

        verify(home).create("Bob");
        verify(home, never()).create("Alice");
    }

    @Test
    void jndiNameCanBeOverridden() throws Exception {
        String jbossName = "ejb:jee6-ejb-examples/jee6-ejb-examples-ejb//ShoppingCartEJB"
                + "!com.example.ejb.cart.ShoppingCartHome?stateful";
        InMemoryJndi.install();
        InMemoryJndi.bind(jbossName, home);
        System.setProperty(JNDI_PROPERTY, jbossName);

        assertTrue(run().startsWith("Looking up " + jbossName + "\n"));
    }

    @Test
    void lookupNarrowsToTheHomeInterface() throws Exception {
        assertSame(home, ShoppingCartRemoteClient.lookupHome(ShoppingCartRemoteClient.DEFAULT_JNDI_NAME));
        assertEquals(Arrays.asList(ShoppingCartHome.class), LocalPortableRemoteObject.narrowedTo());
    }

    @Test
    void homeAndHandleReferencesAreNarrowedNotCast() throws Exception {
        // RMI-IIOP stubs must go through PortableRemoteObject.narrow(); a plain
        // Java cast can fail on a real server.
        run();

        assertEquals(Arrays.<Class<?>>asList(ShoppingCartHome.class, ShoppingCart.class),
                LocalPortableRemoteObject.narrowedTo());
    }

    @Test
    void objectOfWrongTypeIsRejectedByNarrow() {
        InMemoryJndi.bind(ShoppingCartRemoteClient.DEFAULT_JNDI_NAME, "not a home");

        assertThrows(ClassCastException.class, this::run);
    }

    @Test
    void handleToAnotherBeanTypeIsRejectedByNarrow() throws Exception {
        when(handle.getEJBObject()).thenReturn(mock(EJBObject.class));

        assertThrows(ClassCastException.class, this::run);
        verify(cart, never()).remove();
    }

    @Test
    void missingHomeFailsWithNamingException() {
        InMemoryJndi.install(); // nothing bound

        assertThrows(NameNotFoundException.class, this::run);
    }

    @Test
    void createExceptionStopsTheClient() throws Exception {
        when(home.create("Alice")).thenThrow(new CreateException("A customer name is required"));

        assertThrows(CreateException.class, this::run);
        verify(cart, never()).addItem("Java EE 6 Tutorial");
    }

    @Test
    void removedBeanReportedAsWrappedRemoteException() throws Exception {
        // Some servers wrap NoSuchObjectException in another RemoteException.
        doThrow(new RemoteException("bean gone")).when(cart).getItemCount();

        assertTrue(run().endsWith("Expected after remove(): java.rmi.RemoteException: bean gone\n"));
    }

    private String run(String... args) throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            ShoppingCartRemoteClient.main(args);
            return console.output();
        }
    }
}
