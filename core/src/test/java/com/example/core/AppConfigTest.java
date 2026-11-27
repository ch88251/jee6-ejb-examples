package com.example.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.core.testsupport.RedisTestServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/** Tests {@link AppConfig} against a real Redis (Docker). */
class AppConfigTest {

    private RedissonClient redisson;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        redisson = RedisTestServer.newEmptyClient();
        config = new AppConfig(redisson, "test:");
    }

    @AfterEach
    void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void seedsDefaultsWithoutOverwriting() {
        config.seedDefaults();
        assertEquals(new BigDecimal("1.5"), config.getCommissionPercent());

        config.set(AppConfig.COMMISSION_PERCENT, "2.25");
        config.seedDefaults();
        assertEquals(new BigDecimal("2.25"), config.getCommissionPercent());
    }

    @Test
    void valuesArePlainStringsInARedisHash() {
        redisson.getMap("test:config", StringCodec.INSTANCE).put("commissionPercent", " 3 ");

        assertEquals(new BigDecimal("3"), config.getCommissionPercent());
    }

    @Test
    void missingOrInvalidValuesAreErrors() {
        assertThrows(IllegalStateException.class, () -> config.getDecimal("nothing"));
        config.set("bad", "lots");
        assertThrows(IllegalStateException.class, () -> config.getDecimal("bad"));
        assertThrows(NullPointerException.class, () -> new AppConfig(null, "x"));
    }
}
