package com.example.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.client.testsupport.ConsoleCapture;
import com.example.client.testsupport.LocalPortableRemoteObject;
import com.example.ejb.converter.CurrencyConverter;
import com.example.ejb.converter.CurrencyConverterHome;
import com.example.ejb.converter.UnsupportedCurrencyException;
import com.example.ejb.testsupport.InMemoryJndi;
import java.math.BigDecimal;
import java.util.Arrays;
import javax.naming.NameNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link CurrencyConverterRemoteClient}, with the stateless
 * bean's remote home and component interfaces mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // the default conversions are stubbed for every test
class CurrencyConverterRemoteClientTest {

    private static final String JNDI_PROPERTY = "converter.jndi";

    @Mock private CurrencyConverterHome home;
    @Mock private CurrencyConverter converter;
    @Mock private CurrencyConverter another;

    @BeforeAll
    static void installNarrowDelegate() {
        LocalPortableRemoteObject.install();
    }

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind(CurrencyConverterRemoteClient.DEFAULT_JNDI_NAME, home);
        LocalPortableRemoteObject.reset();

        when(home.create()).thenReturn(converter, another);
        when(converter.getSupportedCurrencies()).thenReturn(Arrays.asList("USD", "EUR", "GBP", "JPY", "CAD"));
        when(converter.getCommissionPercent()).thenReturn(new BigDecimal("1.5"));
        when(converter.convert(new BigDecimal("100"), "USD", "EUR")).thenReturn(new BigDecimal("90.62"));
        when(converter.convert(new BigDecimal("250"), "GBP", "JPY")).thenReturn(new BigDecimal("47161.55"));
        when(converter.convert(new BigDecimal("1000"), "JPY", "CAD")).thenReturn(new BigDecimal("8.92"));
        when(converter.convert(BigDecimal.ONE, "USD", "XYZ")).thenThrow(new UnsupportedCurrencyException("XYZ"));
        when(converter.isIdentical(another)).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
        System.clearProperty(JNDI_PROPERTY);
    }

    @Test
    void runsTheSampleConversions() throws Exception {
        String output = run();

        assertEquals(String.join("\n",
                "Looking up " + CurrencyConverterRemoteClient.DEFAULT_JNDI_NAME,
                "Supported currencies: [USD, EUR, GBP, JPY, CAD]",
                "Commission: 1.5%",
                "100 USD = 90.62 EUR",
                "250 GBP = 47161.55 JPY",
                "1000 JPY = 8.92 CAD",
                "Expected application exception: Unsupported currency: XYZ",
                "Two create() results identical = true",
                "Reference removed.",
                ""), output);
    }

    @Test
    void threeArgumentsRunASingleConversion() throws Exception {
        when(converter.convert(new BigDecimal("42.50"), "CAD", "GBP")).thenReturn(new BigDecimal("24.15"));

        String output = run("42.50", "CAD", "GBP");

        assertTrue(output.contains("\n42.50 CAD = 24.15 GBP\n"));
        verify(converter, never()).convert(new BigDecimal("100"), "USD", "EUR");
    }

    @Test
    void otherArgumentCountsFallBackToTheSamples() throws Exception {
        run("42.50", "CAD");

        verify(converter).convert(new BigDecimal("100"), "USD", "EUR");
        verify(converter, never()).convert(new BigDecimal("42.50"), "CAD", "GBP");
    }

    @Test
    void invalidAmountArgumentFails() throws Exception {
        assertThrows(NumberFormatException.class, () -> run("lots", "USD", "EUR"));
        verify(converter, never()).convert(any(), anyString(), anyString());
    }

    @Test
    void createsTwoReferencesAndRemovesOnlyTheFirst() throws Exception {
        run();

        verify(home, times(2)).create();
        verify(converter).isIdentical(another);
        verify(converter).remove();
        verify(another, never()).remove();
    }

    @Test
    void jndiNameCanBeOverridden() throws Exception {
        String jbossName = "ejb:jee6-ejb-examples/jee6-ejb-examples-ejb//CurrencyConverterEJB"
                + "!com.example.ejb.converter.CurrencyConverterHome";
        InMemoryJndi.install();
        InMemoryJndi.bind(jbossName, home);
        System.setProperty(JNDI_PROPERTY, jbossName);

        assertTrue(run().startsWith("Looking up " + jbossName + "\n"));
    }

    @Test
    void missingHomeFailsWithNamingException() {
        InMemoryJndi.install(); // nothing bound

        assertThrows(NameNotFoundException.class, this::run);
    }

    @Test
    void homeIsNarrowedNotCast() throws Exception {
        run();

        assertEquals(Arrays.<Class<?>>asList(CurrencyConverterHome.class), LocalPortableRemoteObject.narrowedTo());
    }

    @Test
    void objectOfWrongTypeIsRejectedByNarrow() {
        InMemoryJndi.bind(CurrencyConverterRemoteClient.DEFAULT_JNDI_NAME, new Object());

        assertThrows(ClassCastException.class, this::run);
    }

    private String run(String... args) throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            CurrencyConverterRemoteClient.main(args);
            return console.output();
        }
    }
}
