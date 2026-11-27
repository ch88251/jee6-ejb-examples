package com.example.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.client.testsupport.ConsoleCapture;
import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link CurrencyConverterRemoteClient#run} with the converter mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CurrencyConverterRemoteClientTest {

    @Mock private CurrencyConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        when(converter.getSupportedCurrencies()).thenReturn(Arrays.asList("USD", "EUR", "GBP", "JPY", "CAD"));
        when(converter.getCommissionPercent()).thenReturn(new BigDecimal("1.5"));
        when(converter.convert(BigDecimal.ONE, "USD", "XYZ")).thenThrow(new UnsupportedCurrencyException("XYZ"));
    }

    @Test
    void printsTheSampleConversions() throws Exception {
        when(converter.convert(new BigDecimal("100"), "USD", "EUR")).thenReturn(new BigDecimal("90.62"));
        when(converter.convert(new BigDecimal("250"), "GBP", "JPY")).thenReturn(new BigDecimal("47161.55"));
        when(converter.convert(new BigDecimal("1000"), "JPY", "CAD")).thenReturn(new BigDecimal("8.92"));

        String out = run();

        assertEquals("Supported currencies: [USD, EUR, GBP, JPY, CAD]\n"
                + "Commission: 1.5%\n"
                + "100 USD = 90.62 EUR\n"
                + "250 GBP = 47161.55 JPY\n"
                + "1000 JPY = 8.92 CAD\n"
                + "Expected application exception: Unsupported currency: XYZ\n", out);
    }

    @Test
    void threeArgumentsMakeOneConversion() throws Exception {
        when(converter.convert(new BigDecimal("5"), "EUR", "GBP")).thenReturn(new BigDecimal("4.23"));

        String out = run("5", "EUR", "GBP");

        assertEquals("Supported currencies: [USD, EUR, GBP, JPY, CAD]\n"
                + "Commission: 1.5%\n"
                + "5 EUR = 4.23 GBP\n"
                + "Expected application exception: Unsupported currency: XYZ\n", out);
        verify(converter, never()).convert(new BigDecimal("100"), "USD", "EUR");
    }

    @Test
    void otherArgumentCountsUseTheSamples() throws Exception {
        when(converter.convert(any(BigDecimal.class), anyString(), anyString())).thenReturn(BigDecimal.TEN);
        when(converter.convert(BigDecimal.ONE, "USD", "XYZ")).thenThrow(new UnsupportedCurrencyException("XYZ"));

        String out = run("5", "EUR");

        assertEquals(true, out.contains("100 USD = 10 EUR\n"), out);
    }

    @Test
    void invalidAmountFails() {
        assertThrows(NumberFormatException.class, () -> run("lots", "USD", "EUR"));
    }

    private String run(String... args) throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            CurrencyConverterRemoteClient.run(converter, args);
            return console.output();
        }
    }
}
