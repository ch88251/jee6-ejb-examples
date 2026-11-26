package com.example.ejb.product;

import java.math.BigDecimal;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;
import javax.ejb.FinderException;

/**
 * EJB 2.x local home for the CMP Product entity.
 * <p>
 * An entity home has three kinds of methods:
 * <ul>
 *   <li>{@code create...}: inserts a new row, implemented by {@code ejbCreate}/{@code ejbPostCreate}</li>
 *   <li>{@code find...}: queries for existing entities. For CMP the container generates them,
 *       {@code findByPrimaryKey} implicitly and the rest from EJB-QL in ejb-jar.xml</li>
 *   <li>home business methods: work not tied to one entity, implemented by {@code ejbHome<Name>}</li>
 * </ul>
 */
public interface ProductLocalHome extends EJBLocalHome {

    ProductLocal create(String sku, String name, BigDecimal price) throws CreateException;

    ProductLocal findByPrimaryKey(String sku) throws FinderException;

    /** EJB-QL: {@code SELECT OBJECT(p) FROM Product AS p ORDER BY p.sku} */
    Collection<ProductLocal> findAll() throws FinderException;

    /** EJB-QL: {@code SELECT OBJECT(p) FROM Product AS p WHERE p.price <= ?1 ORDER BY p.price} */
    Collection<ProductLocal> findByMaxPrice(BigDecimal maxPrice) throws FinderException;

    /** Home business method, implemented by {@link ProductBean#ejbHomeGetAveragePrice()}. */
    BigDecimal getAveragePrice() throws FinderException;
}
