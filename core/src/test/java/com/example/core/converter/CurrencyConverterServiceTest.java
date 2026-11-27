package com.example.core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyConverterService}: conversion math, validation and the commission supplier. */
class CurrencyConverterServiceTest {

    private final AtomicReference<BigDecimal> commission = new AtomicReference<>(new BigDecimal("1.5"));
    private final CurrencyConverterService converter = new CurrencyConverterService(commission::get);

    @Test
    void convertsThroughUsdAndDeductsTheCommission() throws Exception {
        // 100 USD * 0.92 = 92.00 EUR, less 1.5% = 90.62
        assertEquals(new BigDecimal("90.62"), converter.convert(new BigDecimal("100"), "USD", "EUR"));
        // 250 / 0.79 * 151.30 = 47879.75 JPY, less 1.5% = 47161.55
        assertEquals(new BigDecimal("47161.55"), converter.convert(new BigDecimal("250"), "GBP", "JPY"));
    }

    @Test
    void roundsHalfEvenToTwoDecimals() throws Exception {
        commission.set(BigDecimal.ZERO);
        // 1 JPY -> USD = 0.0066093... -> 0.01
        assertEquals(new BigDecimal("0.01"), converter.convert(BigDecimal.ONE, "JPY", "USD"));
        assertEquals(new BigDecimal("0.00"), converter.convert(BigDecimal.ZERO, "USD", "EUR"));
    }

    @Test
    void currencyCodesAreCaseInsensitiveAndTrimmed() throws Exception {
        assertEquals(converter.convert(BigDecimal.TEN, "USD", "EUR"), converter.convert(BigDecimal.TEN, " usd ", "eur"));
    }

    @Test
    void readsTheCommissionOnEveryCall() throws Exception {
        assertEquals(new BigDecimal("1.5"), converter.getCommissionPercent());
        commission.set(new BigDecimal("10"));
        assertEquals(new BigDecimal("10"), converter.getCommissionPercent());
        assertEquals(new BigDecimal("82.80"), converter.convert(new BigDecimal("100"), "USD", "EUR"));
    }

    @Test
    void missingCommissionIsAnError() {
        commission.set(null);
        assertThrows(IllegalStateException.class, converter::getCommissionPercent);
    }

    @Test
    void unknownCurrenciesAreRejected() {
        UnsupportedCurrencyException e = assertThrows(UnsupportedCurrencyException.class,
                () -> converter.convert(BigDecimal.ONE, "USD", "XYZ"));
        assertEquals("Unsupported currency: XYZ", e.getMessage());
        assertThrows(UnsupportedCurrencyException.class, () -> converter.convert(BigDecimal.ONE, null, "USD"));
    }

    @Test
    void negativeOrMissingAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(new BigDecimal("-1"), "USD", "EUR"));
        assertThrows(IllegalArgumentException.class, () -> converter.convert(null, "USD", "EUR"));
    }

    @Test
    void listsTheSupportedCurrenciesAsACopy() {
        assertEquals(Arrays.asList("USD", "EUR", "GBP", "JPY", "CAD"), converter.getSupportedCurrencies());
        converter.getSupportedCurrencies().clear();
        assertEquals(5, converter.getSupportedCurrencies().size());
    }

    @Test
    void requiresACommissionSupplier() {
        assertThrows(NullPointerException.class, () -> new CurrencyConverterService(null));
    }
}
