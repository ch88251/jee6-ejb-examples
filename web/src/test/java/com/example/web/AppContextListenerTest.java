package com.example.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.core.AppServices;
import com.example.core.cart.ShoppingCartService;
import com.example.core.converter.CurrencyConverter;
import com.example.core.pricing.PriceChangeQueue;
import com.example.core.product.ProductCatalog;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link AppContextListener} with {@link AppServices} mocked. */
@ExtendWith(MockitoExtension.class)
class AppContextListenerTest {

    @Mock private AppServices services;
    @Mock private ServletContext context;

    @Test
    void startupStoresEachServiceUnderItsInterfaceName() {
        ShoppingCartService carts = mock(ShoppingCartService.class);
        CurrencyConverter converter = mock(CurrencyConverter.class);
        ProductCatalog catalog = mock(ProductCatalog.class);
        PriceChangeQueue queue = mock(PriceChangeQueue.class);
        when(services.cartService()).thenReturn(carts);
        when(services.converter()).thenReturn(converter);
        when(services.catalog()).thenReturn(catalog);
        when(services.priceChangeQueue()).thenReturn(queue);

        new AppContextListener(() -> services).contextInitialized(new ServletContextEvent(context));

        verify(context).setAttribute(AppServices.class.getName(), services);
        verify(context).setAttribute(ShoppingCartService.class.getName(), carts);
        verify(context).setAttribute(CurrencyConverter.class.getName(), converter);
        verify(context).setAttribute(ProductCatalog.class.getName(), catalog);
        verify(context).setAttribute(PriceChangeQueue.class.getName(), queue);
    }

    @Test
    void shutdownRemovesTheServicesAndClosesThem() {
        when(context.getAttribute(AppServices.class.getName())).thenReturn(services);

        new AppContextListener(() -> services).contextDestroyed(new ServletContextEvent(context));

        verify(context).removeAttribute(ShoppingCartService.class.getName());
        verify(context).removeAttribute(AppServices.class.getName());
        verify(services).close();
    }

    @Test
    void shutdownWithoutServicesDoesNothingElse() {
        new AppContextListener().contextDestroyed(new ServletContextEvent(context));

        verify(context).removeAttribute(AppServices.class.getName());
    }

    @Test
    void serviceLookup() {
        ProductCatalog catalog = mock(ProductCatalog.class);
        when(context.getAttribute(ProductCatalog.class.getName())).thenReturn(catalog);

        assertSame(catalog, AppContextListener.service(context, ProductCatalog.class));
        assertThrows(IllegalStateException.class, () -> AppContextListener.service(context, CurrencyConverter.class));
    }
}
