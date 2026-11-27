package com.example.web;

import com.example.core.AppServices;
import com.example.core.cart.ShoppingCartService;
import com.example.core.converter.CurrencyConverter;
import com.example.core.pricing.PriceChangeQueue;
import com.example.core.product.ProductCatalog;
import java.util.function.Supplier;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Creates the services when the web application starts, and shuts them down
 * when it stops.
 * <p>
 * Each service is stored as a {@link ServletContext} attribute named after its
 * interface. Servlets fetch them in {@code init()} with {@link #service}. This
 * replaces the {@code ejb-local-ref} declarations in web.xml and the JNDI
 * lookups of {@code java:comp/env/ejb/...}. The Redis address comes from the
 * {@code redis.address} system property or the {@code REDIS_ADDRESS}
 * environment variable (default {@code redis://localhost:6379}).
 */
public class AppContextListener implements ServletContextListener {

    static final String SERVICES_ATTR = AppServices.class.getName();

    private final Supplier<AppServices> factory;

    public AppContextListener() {
        this(AppServices::start);
    }

    /** For tests: supplies the services instead of connecting to Redis. */
    AppContextListener(Supplier<AppServices> factory) {
        this.factory = factory;
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        AppServices services = factory.get();
        ctx.setAttribute(SERVICES_ATTR, services);
        ctx.setAttribute(ShoppingCartService.class.getName(), services.cartService());
        ctx.setAttribute(CurrencyConverter.class.getName(), services.converter());
        ctx.setAttribute(ProductCatalog.class.getName(), services.catalog());
        ctx.setAttribute(PriceChangeQueue.class.getName(), services.priceChangeQueue());
        ctx.log("Services started");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        AppServices services = (AppServices) ctx.getAttribute(SERVICES_ATTR);
        ctx.removeAttribute(ShoppingCartService.class.getName());
        ctx.removeAttribute(CurrencyConverter.class.getName());
        ctx.removeAttribute(ProductCatalog.class.getName());
        ctx.removeAttribute(PriceChangeQueue.class.getName());
        ctx.removeAttribute(SERVICES_ATTR);
        if (services != null) {
            services.close();
            ctx.log("Services stopped");
        }
    }

    /**
     * The service of the given type, as stored by {@link #contextInitialized}.
     *
     * @throws IllegalStateException if it is missing (the listener didn't run)
     */
    static <T> T service(ServletContext ctx, Class<T> type) {
        Object service = ctx.getAttribute(type.getName());
        if (service == null) {
            throw new IllegalStateException("No " + type.getSimpleName()
                    + " in the servlet context; is AppContextListener registered in web.xml?");
        }
        return type.cast(service);
    }
}
