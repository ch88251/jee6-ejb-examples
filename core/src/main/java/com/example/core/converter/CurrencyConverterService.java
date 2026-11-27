package com.example.core.converter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The currency converter as a plain object.
 * <p>
 * It used to be a pooled stateless session bean. Since it holds no per-client
 * state and its fields never change after construction, one shared instance is
 * safe for every thread, and no pool is needed.
 * <p>
 * The commission used to be an {@code env-entry} read from JNDI once per pooled
 * instance. Now it is a {@link Supplier} that is asked on every call. In the
 * application it reads the {@code commissionPercent} field of the Redis config
 * hash (see {@link com.example.core.AppConfig}), so it can be changed while the
 * application is running.
 */
public class CurrencyConverterService implements CurrencyConverter {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Units of each currency per 1 USD. Fixed demo values, not real rates. */
    private static final Map<String, BigDecimal> RATES_PER_USD;

    static {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("USD", new BigDecimal("1.00"));
        rates.put("EUR", new BigDecimal("0.92"));
        rates.put("GBP", new BigDecimal("0.79"));
        rates.put("JPY", new BigDecimal("151.30"));
        rates.put("CAD", new BigDecimal("1.37"));
        RATES_PER_USD = Collections.unmodifiableMap(rates);
    }

    private final Supplier<BigDecimal> commissionPercent;

    public CurrencyConverterService(Supplier<BigDecimal> commissionPercent) {
        this.commissionPercent = Objects.requireNonNull(commissionPercent, "commissionPercent");
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency)
            throws UnsupportedCurrencyException {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be zero or positive");
        }
        BigDecimal fromRate = rate(fromCurrency);
        BigDecimal toRate = rate(toCurrency);

        BigDecimal converted = amount.multiply(toRate).divide(fromRate, 10, RoundingMode.HALF_EVEN);
        BigDecimal afterCommission = converted.subtract(
                converted.multiply(getCommissionPercent()).divide(HUNDRED, 10, RoundingMode.HALF_EVEN));
        log("convert " + amount + " " + fromCurrency + " -> " + toCurrency);
        return afterCommission.setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override
    public List<String> getSupportedCurrencies() {
        return new ArrayList<>(RATES_PER_USD.keySet());
    }

    @Override
    public BigDecimal getCommissionPercent() {
        BigDecimal percent = commissionPercent.get();
        if (percent == null) {
            throw new IllegalStateException("No commission percent configured");
        }
        return percent;
    }

    private static BigDecimal rate(String currency) throws UnsupportedCurrencyException {
        BigDecimal rate = currency == null ? null : RATES_PER_USD.get(currency.trim().toUpperCase());
        if (rate == null) {
            throw new UnsupportedCurrencyException(currency);
        }
        return rate;
    }

    private static void log(String event) {
        System.out.println("[CurrencyConverter] " + event);
    }
}
