package com.example.ejb.converter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * EJB 2.x style stateless session bean.
 * <p>
 * Unlike the stateful cart, this bean holds no per-client state. The container
 * keeps a pool of instances and may use a different one for every call, even
 * from the same client. Instance fields may only hold things that are the same
 * for every client: here, the exchange rates and the commission rate, which
 * {@link #ejbCreate()} reads from an {@code env-entry} in ejb-jar.xml.
 * <p>
 * {@code ejbActivate}/{@code ejbPassivate} are never called for stateless beans,
 * and {@code ejbRemove} is called when the container removes an instance from
 * the pool, not when a client calls {@code remove()}.
 */
public class CurrencyConverterBean implements SessionBean {

    private static final long serialVersionUID = 1L;
    private static final String COMMISSION_ENV = "java:comp/env/commissionPercent";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Units of each currency per 1 USD. Fixed demo values, not real rates. */
    private Map<String, BigDecimal> ratesPerUsd;
    private BigDecimal commissionPercent;
    private SessionContext sessionContext;

    /**
     * Called once when the container adds an instance to the pool, not once per
     * client {@code create()} call.
     */
    public void ejbCreate() throws CreateException {
        Map<String, BigDecimal> rates = new LinkedHashMap<String, BigDecimal>();
        rates.put("USD", new BigDecimal("1.00"));
        rates.put("EUR", new BigDecimal("0.92"));
        rates.put("GBP", new BigDecimal("0.79"));
        rates.put("JPY", new BigDecimal("151.30"));
        rates.put("CAD", new BigDecimal("1.37"));
        ratesPerUsd = Collections.unmodifiableMap(rates);

        try {
            // EJB 2.x style: read configuration from the bean's environment (ENC).
            Double percent = (Double) new InitialContext().lookup(COMMISSION_ENV);
            commissionPercent = BigDecimal.valueOf(percent);
        } catch (NamingException e) {
            throw new CreateException("Missing env-entry " + COMMISSION_ENV + ": " + e);
        }
        log("ejbCreate (commission " + commissionPercent + "%)");
    }

    // ---- business methods ----

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency)
            throws UnsupportedCurrencyException {
        if (amount == null || amount.signum() < 0) {
            throw new EJBException("Amount must be zero or positive");
        }
        BigDecimal fromRate = rate(fromCurrency);
        BigDecimal toRate = rate(toCurrency);

        BigDecimal converted = amount.multiply(toRate).divide(fromRate, 10, RoundingMode.HALF_EVEN);
        BigDecimal afterCommission = converted.subtract(
                converted.multiply(commissionPercent).divide(HUNDRED, 10, RoundingMode.HALF_EVEN));
        log("convert " + amount + " " + fromCurrency + " -> " + toCurrency);
        return afterCommission.setScale(2, RoundingMode.HALF_EVEN);
    }

    public List<String> getSupportedCurrencies() {
        return new ArrayList<String>(ratesPerUsd.keySet());
    }

    public BigDecimal getCommissionPercent() {
        return commissionPercent;
    }

    private BigDecimal rate(String currency) throws UnsupportedCurrencyException {
        BigDecimal rate = currency == null ? null : ratesPerUsd.get(currency.trim().toUpperCase());
        if (rate == null) {
            throw new UnsupportedCurrencyException(currency);
        }
        return rate;
    }

    // ---- SessionBean lifecycle callbacks ----

    @Override
    public void setSessionContext(SessionContext ctx) {
        this.sessionContext = ctx;
    }

    @Override
    public void ejbRemove() {
        log("ejbRemove");
    }

    /** Never called for stateless beans, but required by the SessionBean interface. */
    @Override
    public void ejbActivate() {
    }

    /** Never called for stateless beans, but required by the SessionBean interface. */
    @Override
    public void ejbPassivate() {
    }

    private void log(String event) {
        // The identity hash shows which pooled instance served the call.
        System.out.println("[CurrencyConverterBean@" + Integer.toHexString(System.identityHashCode(this))
                + "] " + event);
    }
}
