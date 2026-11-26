package com.example.ejb.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the stateful {@link ShoppingCartBean}. The test plays the
 * container: it calls setSessionContext and ejbCreate first, then the
 * business methods on the same instance, as one client's conversation would.
 */
class ShoppingCartBeanTest {

    private ShoppingCartBean cart;

    @BeforeEach
    void createBean() throws Exception {
        cart = new ShoppingCartBean();
        cart.setSessionContext(null); // the bean only stores it
        cart.ejbCreate("  Alice  ");
    }

    @Test
    void ejbCreateStoresTrimmedCustomerAndStartsEmpty() {
        assertEquals("Alice", cart.getCustomerName());
        assertEquals(0, cart.getItemCount());
        assertTrue(cart.getItems().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void ejbCreateRejectsMissingCustomerName(String name) {
        assertThrows(CreateException.class, () -> new ShoppingCartBean().ejbCreate(name));
    }

    @Test
    void stateAccumulatesAcrossCalls() {
        cart.addItem("Book");
        cart.addItem(" Mug ");
        cart.addItem("Book");

        assertEquals(Arrays.asList("Book", "Mug", "Book"), cart.getItems());
        assertEquals(3, cart.getItemCount());
    }

    @Test
    void removeItemRemovesOneOccurrence() throws Exception {
        cart.addItem("Book");
        cart.addItem("Book");

        cart.removeItem("Book");

        assertEquals(Arrays.asList("Book"), cart.getItems());
    }

    @Test
    void removingUnknownItemThrowsApplicationException() {
        cart.addItem("Book");

        ItemNotFoundException e = assertThrows(ItemNotFoundException.class, () -> cart.removeItem("Unicorn"));
        assertEquals("Item not in cart: Unicorn", e.getMessage());
        assertEquals(1, cart.getItemCount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void addingEmptyItemIsASystemException(String item) {
        // A system exception (EJBException): the container would discard the instance.
        assertThrows(EJBException.class, () -> cart.addItem(item));
    }

    @Test
    void getItemsReturnsReadOnlySnapshot() {
        cart.addItem("Book");
        List<String> snapshot = cart.getItems();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("Hack"));
        cart.addItem("Mug");
        assertEquals(1, snapshot.size(), "an earlier snapshot doesn't change");
        assertNotSame(snapshot, cart.getItems());
    }

    @Test
    void conversationalStateSurvivesPassivation() throws Exception {
        cart.addItem("Book");
        cart.addItem("Mug");

        // Simulate what the container does: ejbPassivate, serialize, deserialize, ejbActivate.
        cart.ejbPassivate();
        ShoppingCartBean restored = roundTrip(cart);
        restored.ejbActivate();

        assertEquals("Alice", restored.getCustomerName());
        assertEquals(Arrays.asList("Book", "Mug"), restored.getItems());
    }

    @Test
    void ejbRemoveReleasesState() {
        cart.addItem("Book");
        cart.ejbRemove();

        // The container never calls a removed instance again; the state is gone.
        assertThrows(NullPointerException.class, () -> cart.getItemCount());
    }

    private static ShoppingCartBean roundTrip(ShoppingCartBean bean) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bean);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ShoppingCartBean) in.readObject();
        }
    }
}
