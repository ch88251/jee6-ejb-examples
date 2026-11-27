package com.example.core.converter;

import java.math.BigDecimal;
import java.util.List;

/** Converts between currencies, less a commission. Stateless and thread-safe. */
public interface CurrencyConverter {

    /** @throws IllegalArgumentException if the amount is missing or negative */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency)
            throws UnsupportedCurrencyException;

    List<String> getSupportedCurrencies();

    BigDecimal getCommissionPercent();
}
