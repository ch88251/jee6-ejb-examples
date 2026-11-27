package com.example.core;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/** Creates the Redisson client that the web application and the remote clients share. */
public final class RedisConnection {

    /** System property with the Redis address, e.g. {@code -Dredis.address=redis://cache:6379}. */
    public static final String ADDRESS_PROPERTY = "redis.address";
    /** Environment variable with the Redis address, used when the system property is not set. */
    public static final String ADDRESS_ENV = "REDIS_ADDRESS";
    public static final String DEFAULT_ADDRESS = "redis://localhost:6379";

    private RedisConnection() {
    }

    /** The configured Redis address: system property, then environment variable, then the default. */
    public static String address() {
        String value = System.getProperty(ADDRESS_PROPERTY);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(ADDRESS_ENV);
        }
        return value == null || value.trim().isEmpty() ? DEFAULT_ADDRESS : value.trim();
    }

    /** Connects to a single Redis server, e.g. {@code redis://localhost:6379}. */
    public static RedissonClient connect(String address) {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }
}
