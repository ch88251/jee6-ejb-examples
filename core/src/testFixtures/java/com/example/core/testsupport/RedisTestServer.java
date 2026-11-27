package com.example.core.testsupport;

import org.junit.jupiter.api.Assumptions;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One throwaway Redis server in Docker (Testcontainers), shared by every test in
 * the JVM. It starts on first use and is removed when the JVM exits.
 * <p>
 * Tests that call {@link #address()} or {@link #newClient()} are skipped, not
 * failed, when Docker isn't available.
 */
public final class RedisTestServer {

    public static final DockerImageName IMAGE = DockerImageName.parse("redis:7.4-alpine");
    private static final int PORT = 6379;

    private static GenericContainer<?> container;

    private RedisTestServer() {
    }

    /** The server's {@code redis://host:port} address. Skips the calling test if Docker is missing. */
    public static synchronized String address() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping tests that need Redis");
        if (container == null) {
            GenericContainer<?> redis = new GenericContainer<>(IMAGE).withExposedPorts(PORT);
            redis.start();
            container = redis;
        }
        return "redis://" + container.getHost() + ":" + container.getMappedPort(PORT);
    }

    /** A new client for the test server. Shut it down after the test. */
    public static RedissonClient newClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(address());
        return Redisson.create(config);
    }

    /** A new client for the test server, with every key deleted first. */
    public static RedissonClient newEmptyClient() {
        RedissonClient client = newClient();
        client.getKeys().flushall();
        return client;
    }
}
