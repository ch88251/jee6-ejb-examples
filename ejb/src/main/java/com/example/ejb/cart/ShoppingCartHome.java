package com.example.ejb.cart;

import java.rmi.RemoteException;
import javax.ejb.CreateException;
import javax.ejb.EJBHome;

/**
 * EJB 2.x remote home interface, for clients in other JVMs (looked up via JNDI
 * and narrowed with PortableRemoteObject.narrow()).
 */
public interface ShoppingCartHome extends EJBHome {

    ShoppingCart create(String customerName) throws CreateException, RemoteException;
}
