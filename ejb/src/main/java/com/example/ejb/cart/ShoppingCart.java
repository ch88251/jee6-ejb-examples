package com.example.ejb.cart;

import java.rmi.RemoteException;
import java.util.List;
import javax.ejb.EJBObject;

/**
 * EJB 2.x remote component interface. Every method must declare RemoteException
 * and all arguments/return values must be serializable.
 */
public interface ShoppingCart extends EJBObject {

    String getCustomerName() throws RemoteException;

    void addItem(String item) throws RemoteException;

    void removeItem(String item) throws ItemNotFoundException, RemoteException;

    List<String> getItems() throws RemoteException;

    int getItemCount() throws RemoteException;
}
