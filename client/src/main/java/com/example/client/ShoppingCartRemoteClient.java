package com.example.client;

import com.example.ejb.cart.ItemNotFoundException;
import com.example.ejb.cart.ShoppingCart;
import com.example.ejb.cart.ShoppingCartHome;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import javax.ejb.Handle;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

/**
 * Standalone (out-of-container) client for the EJB 2.x stateful cart, using
 * its remote view ({@link ShoppingCartHome} / {@link ShoppingCart}).
 * <p>
 * The client runs in its own JVM. It needs the server vendor's client library
 * on the classpath, and JNDI settings for that server, which it reads from
 * {@code jndi.properties} or {@code -Djava.naming.*} system properties.
 * <p>
 * The home's JNDI name depends on the server. Set it with {@code -Dcart.jndi=...};
 * the default is the Java EE 6 portable global name.
 */
public class ShoppingCartRemoteClient {

    static final String DEFAULT_JNDI_NAME =
            "java:global/jee6-ejb-examples/jee6-ejb-examples-ejb/ShoppingCartEJB!com.example.ejb.cart.ShoppingCartHome";

    public static void main(String[] args) throws Exception {
        String jndiName = System.getProperty("cart.jndi", DEFAULT_JNDI_NAME);
        String customer = args.length > 0 ? args[0] : "Alice";

        ShoppingCartHome home = lookupHome(jndiName);

        // create<METHOD> on the home -> ejbCreate<METHOD> on a new bean instance.
        ShoppingCart cart = home.create(customer);
        System.out.println("Created cart for " + cart.getCustomerName());

        // Each call goes to the SAME bean instance, so state builds up between calls.
        cart.addItem("Java EE 6 Tutorial");
        cart.addItem("Coffee mug");
        cart.addItem("Keyboard");
        System.out.println("Items after adding: " + cart.getItems());

        cart.removeItem("Coffee mug");
        System.out.println("Items after removing: " + cart.getItems());

        // An application exception reaches the client as itself, not wrapped.
        try {
            cart.removeItem("Unicorn");
        } catch (ItemNotFoundException e) {
            System.out.println("Expected application exception: " + e.getMessage());
        }

        // A Handle is a serializable reference to this bean instance. It could be
        // saved and used later, or in another JVM, to reach the same conversation.
        Handle handle = cart.getHandle();
        ShoppingCart sameCart = (ShoppingCart) PortableRemoteObject.narrow(
                handle.getEJBObject(), ShoppingCart.class);
        System.out.println("Re-obtained from handle, identical = " + cart.isIdentical(sameCart)
                + ", item count = " + sameCart.getItemCount());

        // remove() ends the conversation; the container calls ejbRemove().
        cart.remove();
        System.out.println("Cart removed.");

        try {
            cart.getItemCount();
        } catch (NoSuchObjectException e) {
            System.out.println("Expected after remove(): " + e.getClass().getSimpleName());
        } catch (RemoteException e) {
            // Some servers wrap the exception in another RemoteException.
            System.out.println("Expected after remove(): " + e);
        }
    }

    /**
     * Looks up the remote home the EJB 2.x way. The object from JNDI may be an
     * RMI-IIOP stub, so it must be narrowed, not just cast.
     */
    static ShoppingCartHome lookupHome(String jndiName) throws NamingException {
        InitialContext ctx = new InitialContext();
        try {
            System.out.println("Looking up " + jndiName);
            Object ref = ctx.lookup(jndiName);
            return (ShoppingCartHome) PortableRemoteObject.narrow(ref, ShoppingCartHome.class);
        } finally {
            ctx.close();
        }
    }
}
