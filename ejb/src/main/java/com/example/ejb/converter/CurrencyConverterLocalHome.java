package com.example.ejb.converter;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

/**
 * EJB 2.x local home for the stateless converter.
 * <p>
 * A stateless bean's home has exactly one create method, with no arguments.
 * Calling it does not necessarily create a bean instance: the container hands
 * out a reference and takes a pooled instance for each business call.
 */
public interface CurrencyConverterLocalHome extends EJBLocalHome {

    CurrencyConverterLocal create() throws CreateException;
}
