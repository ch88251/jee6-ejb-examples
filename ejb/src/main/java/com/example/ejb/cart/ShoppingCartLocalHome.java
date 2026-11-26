package com.example.ejb.cart;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

/**
 * EJB 2.x local home interface. Clients in the same JVM/application (e.g. the
 * servlet in this WAR) use it to create new stateful cart instances.
 */
public interface ShoppingCartLocalHome extends EJBLocalHome {

    /** Maps to {@link ShoppingCartBean#ejbCreate(String)}. */
    ShoppingCartLocal create(String customerName) throws CreateException;
}
