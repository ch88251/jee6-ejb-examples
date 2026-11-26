package com.example.ejb.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.spi.InitialContextFactory;

/**
 * A minimal in-memory JNDI provider for unit tests.
 * <p>
 * The beans look up their environment with {@code new InitialContext().lookup(...)},
 * as EJB 2.x code does. {@link #install()} sets this class as the
 * {@code java.naming.factory.initial}, so those lookups resolve to whatever a
 * test has {@link #bind bound}, e.g. a mocked home under
 * {@code java:comp/env/ejb/Product}. Only {@code lookup}, {@code bind},
 * {@code rebind}, {@code unbind} and {@code close} are supported.
 */
public final class InMemoryJndi implements InitialContextFactory {

    private static final Map<String, Object> BINDINGS = new ConcurrentHashMap<String, Object>();

    /** Makes {@code new InitialContext()} use this provider and clears all bindings. */
    public static void install() {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, InMemoryJndi.class.getName());
        BINDINGS.clear();
    }

    /** Clears all bindings and removes the provider again. */
    public static void uninstall() {
        BINDINGS.clear();
        System.clearProperty(Context.INITIAL_CONTEXT_FACTORY);
    }

    public static void bind(String name, Object value) {
        BINDINGS.put(name, value);
    }

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
            switch (method.getName()) {
                case "lookup":
                    Object value = BINDINGS.get(name);
                    if (value == null) {
                        throw new NameNotFoundException(name);
                    }
                    return value;
                case "bind":
                case "rebind":
                    BINDINGS.put(name, args[1]);
                    return null;
                case "unbind":
                    BINDINGS.remove(name);
                    return null;
                case "close":
                    return null;
                case "toString":
                    return "InMemoryJndi" + BINDINGS.keySet();
                default:
                    throw new UnsupportedOperationException("InMemoryJndi does not support " + method.getName());
            }
        };
        return (Context) Proxy.newProxyInstance(
                InMemoryJndi.class.getClassLoader(), new Class<?>[] {Context.class}, handler);
    }
}
