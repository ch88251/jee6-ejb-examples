package com.example.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ejb.converter.CurrencyConverterLocal;
import com.example.ejb.converter.CurrencyConverterLocalHome;
import com.example.ejb.converter.UnsupportedCurrencyException;
import com.example.ejb.testsupport.InMemoryJndi;
import java.math.BigDecimal;
import java.util.Arrays;
import javax.ejb.CreateException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link CurrencyConverterServlet}: the home cached in init(),
 * a stateless reference created per request, and nothing stored in the session.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // render() stubs are shared by all tests
class CurrencyConverterServletTest {

    @Mock private CurrencyConverterLocalHome home;
    @Mock private CurrencyConverterLocal converter;
    @Mock private ServletConfig config;

    private CurrencyConverterServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/ejb/CurrencyConverter", home);
        when(home.create()).thenReturn(converter);
        when(converter.getSupportedCurrencies()).thenReturn(Arrays.asList("USD", "EUR", "GBP"));
        when(converter.getCommissionPercent()).thenReturn(new BigDecimal("1.5"));

        servlet = new CurrencyConverterServlet();
        servlet.init(config);
        http = new HttpFixture();
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void initFailsWhenHomeIsNotBound() {
        InMemoryJndi.install(); // nothing bound
        assertThrows(ServletException.class, () -> new CurrencyConverterServlet().init(config));
    }

    @Test
    void getRendersFormWithDefaults() throws Exception {
        servlet.doGet(http.request, http.response);

        String html = http.html();
        assertTrue(html.contains("Commission: 1.5%"));
        assertTrue(html.contains("<option selected>USD</option>"), "from defaults to USD");
        assertTrue(html.contains("name='amount' value='100'"));
        verify(converter, never()).convert(any(), anyString(), anyString());
    }

    @Test
    void postConvertsAndShowsResult() throws Exception {
        when(converter.convert(new BigDecimal("100"), "USD", "EUR")).thenReturn(new BigDecimal("90.62"));

        servlet.doPost(http.param("amount", " 100 ").param("from", "USD").param("to", "EUR").request, http.response);

        assertTrue(http.html().contains("<strong>100 USD = 90.62 EUR</strong>"));
    }

    @Test
    void eachRequestCreatesAReferenceAndNothingGoesInTheSession() throws Exception {
        servlet.doGet(http.request, http.response);
        HttpFixture second = http.nextRequest();
        servlet.doGet(second.request, second.response);

        verify(home, times(2)).create();
        verify(http.request, never()).getSession();
        verify(second.request, never()).getSession();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingAmountIsRejectedBeforeCallingTheBean(String amount) throws Exception {
        servlet.doPost(http.param("amount", amount).param("from", "USD").param("to", "EUR").request, http.response);

        assertTrue(http.html().contains("Please enter an amount."));
        verify(converter, never()).convert(any(), anyString(), anyString());
    }

    @Test
    void invalidAmountShowsMessage() throws Exception {
        servlet.doPost(http.param("amount", "abc").param("from", "USD").param("to", "EUR").request, http.response);

        assertTrue(http.html().contains("Not a valid amount: abc"));
    }

    @Test
    void unsupportedCurrencyShowsApplicationException() throws Exception {
        when(converter.convert(any(), anyString(), anyString())).thenThrow(new UnsupportedCurrencyException("XYZ"));

        servlet.doPost(http.param("amount", "1").param("from", "USD").param("to", "XYZ").request, http.response);

        assertTrue(http.html().contains("Unsupported currency: XYZ"));
    }

    @Test
    void createExceptionFailsTheRequest() throws Exception {
        when(home.create()).thenThrow(new CreateException("pool exhausted"));

        assertThrows(ServletException.class, () -> servlet.doGet(http.request, http.response));
    }

    @Test
    void echoedAmountIsHtmlEscaped() throws Exception {
        servlet.doPost(http.param("amount", "'><script>").param("from", "USD").param("to", "EUR").request,
                http.response);

        assertFalse(http.html().contains("<script>"));
        assertTrue(http.html().contains("&#39;&gt;&lt;script&gt;"));
    }
}
