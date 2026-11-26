package com.example.ejb.converter;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.List;
import javax.ejb.EJBObject;

/** EJB 2.x remote component interface of the stateless converter. */
public interface CurrencyConverter extends EJBObject {

    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency)
            throws UnsupportedCurrencyException, RemoteException;

    List<String> getSupportedCurrencies() throws RemoteException;

    BigDecimal getCommissionPercent() throws RemoteException;
}
