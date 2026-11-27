package com.example.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.core.AppServices;
import com.example.core.pricing.PriceChangeQueue;
import com.example.core.product.ProductCatalog;
import com.example.core.testsupport.RedisTestServer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The listener and servlets together with the real services and a Redis in
 * Docker: sample products, then a price change through the queue and worker.
 */
class WebIntegrationTest {

    private final Map<String, Object> attributes = new HashMap<>();
    private final ServletContext context = mock(ServletContext.class);
    private final AppContextListener listener =
            new AppContextListener(() -> AppServices.start(RedisTestServer.newEmptyClient(), true));

    @BeforeEach
    void setUp() {
        when(context.getAttribute(anyString()))
                .thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));
        doAnswer(inv -> attributes.put(inv.getArgument(0), inv.getArgument(1)))
                .when(context).setAttribute(anyString(), any());
        doAnswer(inv -> attributes.remove(inv.<String>getArgument(0)))
                .when(context).removeAttribute(anyString());
        listener.contextInitialized(new ServletContextEvent(context));
    }

    @AfterEach
    void tearDown() {
        listener.contextDestroyed(new ServletContextEvent(context));
    }

    @Test
    void sampleProductsThenAnAsynchronousPriceChange() throws Exception {
        ProductServlet products = init(new ProductServlet());
        PriceChangeServlet pricing = init(new PriceChangeServlet());

        HttpFixture sample = new HttpFixture().param("action", "sample");
        products.doPost(sample.request, sample.response);
        assertTrue(sample.html().contains("Created 4 sample product(s)."), sample.html());
        assertTrue(sample.html().contains("<td>MG-002</td><td>Coffee mug</td><td>9.50</td>"));

        HttpFixture send = new HttpFixture().param("sku", "mg-002").param("percent", "10");
        pricing.doPost(send.request, send.response);
        assertTrue(send.html().contains("10% off MG-002"));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            HttpFixture page = new HttpFixture();
            products.doGet(page.request, page.response);
            if (page.html().contains("<td>MG-002</td><td>Coffee mug</td><td>8.55</td>")) {
                break;
            }
            assertTrue(Instant.now().isBefore(deadline), "the worker did not apply the discount");
            Thread.sleep(50);
        }
        assertTrue(attributes.get(ProductCatalog.class.getName()) != null);
        assertTrue(attributes.get(PriceChangeQueue.class.getName()) != null);
    }

    private <S extends HttpServlet> S init(S servlet) throws Exception {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        return servlet;
    }
}
