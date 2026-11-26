package com.example.ejb.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ejb.testsupport.InMemoryJndi;
import java.math.BigDecimal;
import java.util.Arrays;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for the stateless {@link CurrencyConverterBean}. The env-entry
 * {@code commissionPercent} from ejb-jar.xml is supplied through the
 * in-memory JNDI.
 */
class CurrencyConverterBeanTest {

    private static final String COMMISSION = "java:comp/env/commissionPercent";

    private CurrencyConverterBean converter;

    @BeforeEach
    void createBean() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind(COMMISSION, 1.5d); // same value as ejb-jar.xml
        converter = newBean();
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void ejbCreateReadsCommissionFromEnvironment() {
        assertEquals(new BigDecimal("1.5"), converter.getCommissionPercent());
    }

    @Test
    void ejbCreateFailsWithoutEnvEntry() {
        InMemoryJndi.install(); // nothing bound
        CreateException e = assertThrows(CreateException.class, CurrencyConverterBeanTest::newBean);
        assertEquals(true, e.getMessage().contains(COMMISSION));
    }

    @ParameterizedTest(name = "{0} {1} -> {2} = {3}")
    @CsvSource({
        // amount, from, to, expected (rate conversion, then 1.5% commission off)
        "100,  USD, EUR, 90.62",   // 100 * 0.92 = 92.00, minus 1.38
        "100,  EUR, USD, 107.07",  // 100 / 0.92 = 108.6956..., minus 1.5%
        "250,  GBP, JPY, 47161.55",  // 250 * 151.30 / 0.79, minus 1.5%
        "100,  USD, USD, 98.50",   // same currency: only the commission
        "0,    USD, EUR, 0.00",
    })
    void convertAppliesRatesAndCommission(String amount, String from, String to, String expected)
            throws Exception {
        assertEquals(new BigDecimal(expected), converter.convert(new BigDecimal(amount), from, to));
    }

    @Test
    void currencyCodesAreCaseInsensitiveAndTrimmed() throws Exception {
        assertEquals(new BigDecimal("90.62"), converter.convert(new BigDecimal("100"), " usd ", "eur"));
    }

    @Test
    void commissionComesFromTheEnvironment() throws Exception {
        InMemoryJndi.bind(COMMISSION, 0d);
        CurrencyConverterBean noCommission = newBean();

        assertEquals(new BigDecimal("92.00"), noCommission.convert(new BigDecimal("100"), "USD", "EUR"));
    }

    @Test
    void unknownCurrencyIsAnApplicationException() {
        UnsupportedCurrencyException e = assertThrows(UnsupportedCurrencyException.class,
                () -> converter.convert(BigDecimal.ONE, "USD", "XYZ"));
        assertEquals("Unsupported currency: XYZ", e.getMessage());
        assertThrows(UnsupportedCurrencyException.class, () -> converter.convert(BigDecimal.ONE, null, "USD"));
    }

    @Test
    void negativeOrMissingAmountIsASystemException() {
        assertThrows(EJBException.class, () -> converter.convert(new BigDecimal("-1"), "USD", "EUR"));
        assertThrows(EJBException.class, () -> converter.convert(null, "USD", "EUR"));
    }

    @Test
    void supportedCurrenciesAreListedInOrder() {
        assertEquals(Arrays.asList("USD", "EUR", "GBP", "JPY", "CAD"), converter.getSupportedCurrencies());
    }

    @Test
    void instancesAreInterchangeable() throws Exception {
        // Stateless: any pooled instance gives the same answer for the same input.
        CurrencyConverterBean other = newBean();
        BigDecimal amount = new BigDecimal("42.42");
        assertEquals(converter.convert(amount, "CAD", "GBP"), other.convert(amount, "CAD", "GBP"));
    }

    private static CurrencyConverterBean newBean() throws CreateException {
        CurrencyConverterBean bean = new CurrencyConverterBean();
        bean.setSessionContext(null); // the bean only stores it
        bean.ejbCreate();
        return bean;
    }
}
