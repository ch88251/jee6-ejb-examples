package com.example.core;

import java.math.BigDecimal;
import java.util.Objects;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * Application settings, kept in one Redis hash ({@code <prefix>config}).
 * <p>
 * This replaces the {@code env-entry} values that the beans used to read from
 * JNDI ({@code java:comp/env/...}). Values are strings, so they can be read and
 * changed with {@code redis-cli}, for example
 * {@code HSET jee6-ejb-examples:config commissionPercent 2.0}, and the change
 * takes effect on the next call, without a redeploy.
 */
public class AppConfig {

    public static final String COMMISSION_PERCENT = "commissionPercent";
    public static final String DEFAULT_COMMISSION_PERCENT = "1.5";

    private final RMap<String, String> settings;

    public AppConfig(RedissonClient redisson, String keyPrefix) {
        Objects.requireNonNull(redisson, "redisson");
        this.settings = redisson.getMap(keyPrefix + "config", StringCodec.INSTANCE);
    }

    /** Writes the default for each setting that has no value yet. Existing values are kept. */
    public void seedDefaults() {
        settings.fastPutIfAbsent(COMMISSION_PERCENT, DEFAULT_COMMISSION_PERCENT);
    }

    /**
     * Reads a decimal setting.
     *
     * @throws IllegalStateException if the setting is missing or not a number
     */
    public BigDecimal getDecimal(String name) {
        String value = settings.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing setting " + name + " in Redis hash " + settings.getName());
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Setting " + name + " is not a number: " + value);
        }
    }

    public BigDecimal getCommissionPercent() {
        return getDecimal(COMMISSION_PERCENT);
    }

    public void set(String name, String value) {
        settings.fastPut(name, value);
    }
}
