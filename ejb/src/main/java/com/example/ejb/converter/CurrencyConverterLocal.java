package com.example.ejb.converter;

import java.math.BigDecimal;
import java.util.List;
import javax.ejb.EJBLocalObject;

/** EJB 2.x local component interface of the stateless converter. */
public interface CurrencyConverterLocal extends EJBLocalObject {

    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency)
            throws UnsupportedCurrencyException;

    List<String> getSupportedCurrencies();

    BigDecimal getCommissionPercent();
}
