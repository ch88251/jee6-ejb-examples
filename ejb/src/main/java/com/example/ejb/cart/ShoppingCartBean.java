package com.example.ejb.cart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

/**
 * EJB 2.x style stateful session bean.
 * <p>
 * No annotations are used: the bean is declared in {@code WEB-INF/ejb-jar.xml}
 * and implements the {@link SessionBean} callback contract. Its instance fields
 * are the conversational state kept for one client between calls, so they must
 * be serializable (or transient and rebuilt in {@link #ejbActivate()}) for
 * passivation.
 */
public class ShoppingCartBean implements SessionBean {

    private static final long serialVersionUID = 1L;

    // ---- conversational state ----
    private String customerName;
    private ArrayList<String> items;

    // The container restores SessionContext references after passivation,
    // so it does not need to be transient.
    private SessionContext sessionContext;

    // ---- create methods: one ejbCreate<METHOD> per home create<METHOD> ----

    public void ejbCreate(String customerName) throws CreateException {
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new CreateException("A customer name is required");
        }
        this.customerName = customerName.trim();
        this.items = new ArrayList<String>();
        log("ejbCreate");
    }

    // ---- business methods ----

    public String getCustomerName() {
        return customerName;
    }

    public void addItem(String item) {
        if (item == null || item.trim().isEmpty()) {
            throw new EJBException("Item must not be empty");
        }
        items.add(item.trim());
    }

    public void removeItem(String item) throws ItemNotFoundException {
        if (!items.remove(item)) {
            throw new ItemNotFoundException(item);
        }
    }

    public List<String> getItems() {
        // Return a copy so local clients cannot mutate the bean's state directly.
        return Collections.unmodifiableList(new ArrayList<String>(items));
    }

    public int getItemCount() {
        return items.size();
    }

    // ---- SessionBean lifecycle callbacks ----

    @Override
    public void setSessionContext(SessionContext ctx) {
        this.sessionContext = ctx;
    }

    @Override
    public void ejbRemove() {
        log("ejbRemove");
        items = null;
    }

    @Override
    public void ejbActivate() {
        log("ejbActivate");
    }

    @Override
    public void ejbPassivate() {
        log("ejbPassivate");
    }

    private void log(String event) {
        System.out.println("[ShoppingCartBean] " + event + " (customer=" + customerName + ")");
    }
}
