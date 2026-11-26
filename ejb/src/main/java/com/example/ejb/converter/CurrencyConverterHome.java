package com.example.ejb.converter;

import java.rmi.RemoteException;
import javax.ejb.CreateException;
import javax.ejb.EJBHome;

/** EJB 2.x remote home for the stateless converter: one no-argument create(). */
public interface CurrencyConverterHome extends EJBHome {

    CurrencyConverter create() throws CreateException, RemoteException;
}
