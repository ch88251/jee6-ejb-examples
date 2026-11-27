package com.example.core;

import com.example.core.cart.RedisShoppingCartService;
import com.example.core.cart.ShoppingCartService;
import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.CurrencyConverterService;
import com.example.core.pricing.PriceChangeHandler;
import com.example.core.pricing.PriceChangeQueue;
import com.example.core.pricing.PriceChangeWorker;
import com.example.core.pricing.RedisPriceChangeQueue;
import com.example.core.product.ProductCatalog;
import com.example.core.product.RedisProductCatalog;
import java.time.Duration;
import java.util.Objects;
import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;

/**
 * Builds and wires the application's services. This is the "composition root",
 * and it replaces the EJB container and JNDI:
 * <ul>
 *   <li>Services are created here with {@code new} and handed their
 *       dependencies through constructors, not looked up by name
 *       ({@code ejb-local-ref}).</li>
 *   <li>Settings come from the Redis config hash ({@link AppConfig}), not from
 *       {@code env-entry}.</li>
 *   <li>The price change worker is started here, as the container used to start
 *       the message-driven bean.</li>
 *   <li>The cart and converter are published through Redisson RemoteService, so
 *       standalone clients can call them, as they used to through the remote
 *       EJB 2.x views.</li>
 * </ul>
 * The web application creates one in its {@code ServletContextListener}.
 */
public final class AppServices implements AutoCloseable {

    /** Prefix of every Redis key the application uses. */
    public static final String KEY_PREFIX = "jee6-ejb-examples:";
    /** Name of the Redisson RemoteService that the cart and converter are published under. */
    public static final String REMOTE_SERVICE_NAME = KEY_PREFIX + "remote";
    public static final Duration CART_TIMEOUT = Duration.ofMinutes(30);
    public static final int PRICE_CHANGE_MAX_ATTEMPTS = 5;
    /** Threads per remotely published service. */
    private static final int REMOTE_WORKERS = 4;

    private final RedissonClient redisson;
    private final boolean ownsClient;
    private final AppConfig config;
    private final ShoppingCartService cartService;
    private final CurrencyConverter converter;
    private final ProductCatalog catalog;
    private final RedisPriceChangeQueue priceChangeQueue;
    private final PriceChangeWorker priceChangeWorker;
    private final RRemoteService remoteService;

    private AppServices(RedissonClient redisson, boolean ownsClient) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
        this.ownsClient = ownsClient;
        this.config = new AppConfig(redisson, KEY_PREFIX);
        this.cartService = new RedisShoppingCartService(redisson, KEY_PREFIX, CART_TIMEOUT);
        this.converter = new CurrencyConverterService(config::getCommissionPercent);
        this.catalog = new RedisProductCatalog(redisson, KEY_PREFIX);
        this.priceChangeQueue = new RedisPriceChangeQueue(redisson, KEY_PREFIX);
        this.priceChangeWorker = new PriceChangeWorker(
                priceChangeQueue, new PriceChangeHandler(catalog), PRICE_CHANGE_MAX_ATTEMPTS);
        this.remoteService = redisson.getRemoteService(REMOTE_SERVICE_NAME);
    }

    /** Connects to the configured Redis ({@link RedisConnection#address()}) and starts the services. */
    public static AppServices start() {
        return start(RedisConnection.connect(RedisConnection.address()), true);
    }

    /**
     * Starts the services on an existing client: writes default settings,
     * starts the price change worker and publishes the remote services.
     *
     * @param ownsClient whether {@link #close()} should also shut the client down
     */
    public static AppServices start(RedissonClient redisson, boolean ownsClient) {
        AppServices services = new AppServices(redisson, ownsClient);
        try {
            services.config.seedDefaults();
            services.priceChangeWorker.start();
            services.remoteService.register(ShoppingCartService.class, services.cartService, REMOTE_WORKERS);
            services.remoteService.register(CurrencyConverter.class, services.converter, REMOTE_WORKERS);
        } catch (RuntimeException e) {
            services.close();
            throw e;
        }
        return services;
    }

    public AppConfig config() {
        return config;
    }

    public ShoppingCartService cartService() {
        return cartService;
    }

    public CurrencyConverter converter() {
        return converter;
    }

    public ProductCatalog catalog() {
        return catalog;
    }

    public PriceChangeQueue priceChangeQueue() {
        return priceChangeQueue;
    }

    /** Stops the worker, withdraws the remote services and, if it owns it, shuts the client down. */
    @Override
    public void close() {
        priceChangeWorker.close();
        remoteService.deregister(ShoppingCartService.class);
        remoteService.deregister(CurrencyConverter.class);
        if (ownsClient) {
            redisson.shutdown();
        }
    }
}
