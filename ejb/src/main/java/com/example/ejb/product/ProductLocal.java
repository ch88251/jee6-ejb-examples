package com.example.ejb.product;

import java.math.BigDecimal;
import javax.ejb.EJBLocalObject;

/**
 * EJB 2.x local component interface of the Product entity. Each instance
 * represents one row. Calling a setter changes the persistent state, which
 * the container writes to the database when the transaction commits.
 * <p>
 * The primary key ({@code sku}) has no setter here: it can't change after
 * {@code create}.
 */
public interface ProductLocal extends EJBLocalObject {

    String getSku();

    String getName();

    void setName(String name);

    BigDecimal getPrice();

    void setPrice(BigDecimal price);

    /** Business logic that lives on the entity itself. */
    void applyDiscount(int percent);
}
