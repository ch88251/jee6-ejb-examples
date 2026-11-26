package com.example.ejb.cart;

import java.util.List;
import javax.ejb.EJBLocalObject;

/**
 * EJB 2.x local component interface: the business methods visible to local clients.
 */
public interface ShoppingCartLocal extends EJBLocalObject {

    String getCustomerName();

    void addItem(String item);

    void removeItem(String item) throws ItemNotFoundException;

    List<String> getItems();

    int getItemCount();
}
