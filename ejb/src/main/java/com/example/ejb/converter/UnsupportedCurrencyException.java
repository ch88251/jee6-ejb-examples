package com.example.ejb.converter;

/** Application exception thrown for a currency code the converter does not know. */
public class UnsupportedCurrencyException extends Exception {

    private static final long serialVersionUID = 1L;

    public UnsupportedCurrencyException(String currency) {
        super("Unsupported currency: " + currency);
    }
}
