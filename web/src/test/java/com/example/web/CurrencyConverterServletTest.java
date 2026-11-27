package com.example.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.util.Arrays;
import javax.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link CurrencyConverterServlet} with the converter mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CurrencyConverterServletTest {

    @Mock private CurrencyConverter converter;

    private CurrencyConverterServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        servlet = ServletSetup.init(new CurrencyConverterServlet(), CurrencyConverter.class, converter);
        http = new HttpFixture();
        when(converter.getSupportedCurrencies()).thenReturn(Arrays.asList("USD", "EUR", "GBP"));
        when(converter.getCommissionPercent()).thenReturn(new BigDecimal("1.5"));
    }

    @Test
    void initFailsWithoutTheService() {
        assertThrows(ServletException.class,
                () -> ServletSetup.init(new CurrencyConverterServlet(), String.class, "unrelated"));
    }

    @Test
    void getRendersFormWithDefaults() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("Commission: 1.5%"));
        assertTrue(http.html().contains("value='100'"));
        assertTrue(http.html().contains("<option selected>USD</option>"));
        assertTrue(http.html().contains("<option selected>EUR</option>"));
    }

    @Test
    void postConvertsAndShowsResult() throws Exception {
        when(converter.convert(new BigDecimal("100"), "USD", "EUR")).thenReturn(new BigDecimal("90.62"));

        servlet.doPost(http.param("amount", " 100 ").param("from", "USD").param("to", "EUR").request, http.response);

        assertTrue(http.html().contains("100 USD = 90.62 EUR"));
    }

    @Test
    void nothingGoesInTheSession() throws Exception {
        when(converter.convert(any(), anyString(), anyString())).thenReturn(BigDecimal.ONE);

        servlet.doPost(http.param("amount", "1").param("from", "USD").param("to", "EUR").request, http.response);

        verifyNoInteractions(http.session);
    }

    @Test
    void missingOrInvalidAmountsAreRejectedBeforeCallingTheConverter() throws Exception {
        servlet.doPost(http.param("amount", " ").request, http.response);
        HttpFixture second = new HttpFixture().param("amount", "lots").param("from", "USD").param("to", "EUR");
        servlet.doPost(second.request, second.response);

        assertTrue(http.html().contains("Please enter an amount."));
        assertTrue(second.html().contains("Not a valid amount: lots"));
        verify(converter, never()).convert(any(), anyString(), anyString());
    }

    @Test
    void negativeAmountShowsTheServiceMessage() throws Exception {
        when(converter.convert(new BigDecimal("-5"), "USD", "EUR"))
                .thenThrow(new IllegalArgumentException("Amount must be zero or positive"));

        servlet.doPost(http.param("amount", "-5").param("from", "USD").param("to", "EUR").request, http.response);

        assertTrue(http.html().contains("Amount must be zero or positive"));
    }

    @Test
    void unsupportedCurrencyShowsApplicationException() throws Exception {
        when(converter.convert(BigDecimal.ONE, "USD", "XYZ")).thenThrow(new UnsupportedCurrencyException("XYZ"));

        servlet.doPost(http.param("amount", "1").param("from", "USD").param("to", "XYZ").request, http.response);

        assertTrue(http.html().contains("Unsupported currency: XYZ"));
    }

    @Test
    void echoedAmountIsHtmlEscaped() throws Exception {
        servlet.doPost(http.param("amount", "<x>'").request, http.response);

        assertTrue(http.html().contains("Not a valid amount: &lt;x&gt;&#39;"));
        assertFalse(http.html().contains("<x>"));
    }
}
