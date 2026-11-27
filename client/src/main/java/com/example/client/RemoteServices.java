package com.example.client;

import com.example.core.AppServices;
import com.example.core.RedisConnection;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;

/**
 * Gets a client-side proxy for a service the web application publishes through
 * Redisson RemoteService. This replaces the JNDI lookup and
 * {@code PortableRemoteObject.narrow()} of an EJB 2.x remote home.
 * <p>
 * A call on the proxy becomes a request in Redis. A worker in the web
 * application picks it up, runs the method on the real service, and sends back
 * the result or the exception. Arguments and results travel as serialized
 * objects, as with RMI.
 */
final class RemoteServices {

    /** How long to wait for a server to pick up a call before giving up. */
    static final long ACK_TIMEOUT_SECONDS = 2;
    static final long EXECUTION_TIMEOUT_SECONDS = 30;

    private RemoteServices() {
    }

    static RedissonClient connect() {
        String address = RedisConnection.address();
        System.out.println("Connecting to " + address);
        return RedisConnection.connect(address);
    }

    static <T> T proxy(RedissonClient redisson, Class<T> type) {
        RemoteInvocationOptions options = RemoteInvocationOptions.defaults()
                .expectAckWithin(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .expectResultWithin(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return redisson.getRemoteService(AppServices.REMOTE_SERVICE_NAME).get(type, options);
    }
}
