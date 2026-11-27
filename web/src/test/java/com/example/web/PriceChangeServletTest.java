package com.example.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.core.pricing.PriceChangeQueue;
import javax.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link PriceChangeServlet} with the queue mocked. */
@ExtendWith(MockitoExtension.class)
class PriceChangeServletTest {

    @Mock private PriceChangeQueue queue;

    private PriceChangeServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        servlet = ServletSetup.init(new PriceChangeServlet(), PriceChangeQueue.class, queue);
        http = new HttpFixture();
    }

    @Test
    void initFailsWithoutTheQueue() {
        assertThrows(ServletException.class,
                () -> ServletSetup.init(new PriceChangeServlet(), String.class, "unrelated"));
    }

    @Test
    void getRendersFormWithoutSending() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("name='sku'"));
        verifyNoInteractions(queue);
    }

    @Test
    void postSendsTrimmedUpperCaseSkuAndPercent() throws Exception {
        when(queue.send("MG-002", 10)).thenReturn("msg-1");

        servlet.doPost(http.param("sku", " mg-002 ").param("percent", " 10 ").request, http.response);

        verify(queue).send("MG-002", 10);
        assertTrue(http.html().contains("Queued message msg-1: 10% off MG-002."));
    }

    @Test
    void outOfRangeDiscountIsSentAnywayForTheWorkerToDiscard() throws Exception {
        when(queue.send("MG-002", 150)).thenReturn("msg-2");

        servlet.doPost(http.param("sku", "MG-002").param("percent", "150").request, http.response);

        verify(queue).send("MG-002", 150);
    }

    @Test
    void invalidInputSendsNothing() throws Exception {
        servlet.doPost(http.param("sku", "MG-002").request, http.response);
        HttpFixture noSku = new HttpFixture().param("percent", "10");
        servlet.doPost(noSku.request, noSku.response);

        assertTrue(http.html().contains("Please enter a whole-number percentage."));
        assertTrue(noSku.html().contains("Please enter a SKU."));
        verify(queue, never()).send(anyString(), anyInt());
    }

    @Test
    void sendFailureFailsTheRequest() {
        when(queue.send("MG-002", 10)).thenThrow(new IllegalStateException("Redis is down"));

        assertThrows(IllegalStateException.class,
                () -> servlet.doPost(http.param("sku", "MG-002").param("percent", "10").request, http.response));
    }

    @Test
    void echoedSkuIsHtmlEscaped() throws Exception {
        servlet.doPost(http.param("sku", "<b>").param("percent", "x").request, http.response);

        assertTrue(http.html().contains("value='&lt;b&gt;'"));
        assertFalse(http.html().contains("<b>"));
    }
}
