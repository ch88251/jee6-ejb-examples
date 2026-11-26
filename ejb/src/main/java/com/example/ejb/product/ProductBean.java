package com.example.ejb.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;

/**
 * EJB 2.x container-managed persistence (CMP 2.x) entity bean.
 * <p>
 * The class is abstract: persistent fields are declared only as abstract
 * get/set pairs (cmp-fields), and at deploy time the container generates a
 * subclass that implements them and does all the JDBC. Finder and
 * {@code ejbSelect} queries are written in EJB-QL in ejb-jar.xml, not in Java.
 * How the "Product" abstract schema maps to a table is vendor-specific (see
 * glassfish-ejb-jar.xml).
 */
public abstract class ProductBean implements EntityBean {

    private static final long serialVersionUID = 1L;

    private EntityContext entityContext;

    // ---- CMP fields: implemented by the container ----

    public abstract String getSku();

    public abstract void setSku(String sku);

    public abstract String getName();

    public abstract void setName(String name);

    public abstract BigDecimal getPrice();

    public abstract void setPrice(BigDecimal price);

    // ---- select method: an internal EJB-QL query, not exposed to clients ----

    /** EJB-QL: {@code SELECT p.price FROM Product AS p} */
    public abstract Collection<BigDecimal> ejbSelectAllPrices() throws FinderException;

    // ---- create: ejbCreate sets the CMP fields, then the container INSERTs ----

    /** For CMP, ejbCreate returns null; the container builds the primary key from the cmp-fields. */
    public String ejbCreate(String sku, String name, BigDecimal price) throws CreateException {
        if (sku == null || sku.trim().isEmpty()) {
            throw new CreateException("SKU is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new CreateException("Name is required");
        }
        if (price == null || price.signum() < 0) {
            throw new CreateException("Price must be zero or positive");
        }
        setSku(sku.trim().toUpperCase());
        setName(name.trim());
        setPrice(price.setScale(2, RoundingMode.HALF_EVEN));
        return null;
    }

    /**
     * Called after the INSERT, once the entity has an identity. This is the
     * place to set container-managed relationships or pass
     * entityContext.getEJBLocalObject() to others; neither is allowed in ejbCreate.
     */
    public void ejbPostCreate(String sku, String name, BigDecimal price) {
    }

    // ---- business methods ----

    public void applyDiscount(int percent) {
        if (percent <= 0 || percent >= 100) {
            throw new EJBException("Discount must be between 1 and 99 percent");
        }
        BigDecimal factor = BigDecimal.valueOf(100 - percent).divide(BigDecimal.valueOf(100));
        setPrice(getPrice().multiply(factor).setScale(2, RoundingMode.HALF_EVEN));
    }

    // ---- home business method: runs on a pooled instance with no identity ----

    public BigDecimal ejbHomeGetAveragePrice() throws FinderException {
        Collection<BigDecimal> prices = ejbSelectAllPrices();
        if (prices.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal price : prices) {
            total = total.add(price);
        }
        return total.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_EVEN);
    }

    // ---- EntityBean lifecycle callbacks ----
    // With CMP the container does the SQL, so these are usually empty or only
    // derive/clean up non-persistent state.

    @Override
    public void setEntityContext(EntityContext ctx) {
        this.entityContext = ctx;
    }

    @Override
    public void unsetEntityContext() {
        this.entityContext = null;
    }

    /** After the container has loaded the cmp-fields (SELECT). */
    @Override
    public void ejbLoad() {
    }

    /** Before the container writes the cmp-fields (UPDATE). */
    @Override
    public void ejbStore() {
    }

    /** Before the container deletes the row: the client called remove(). */
    @Override
    public void ejbRemove() {
        System.out.println("[ProductBean] ejbRemove " + entityContext.getPrimaryKey());
    }

    /** The instance leaves the pool to take on an identity (a primary key). */
    @Override
    public void ejbActivate() {
    }

    /** The instance goes back to the pool and gives up its identity. */
    @Override
    public void ejbPassivate() {
    }
}
