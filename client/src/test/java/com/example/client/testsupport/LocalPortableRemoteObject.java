package com.example.client.testsupport;

import java.rmi.Remote;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.rmi.CORBA.PortableRemoteObjectDelegate;

/**
 * In-JVM stand-in for the ORB's PortableRemoteObject implementation, used in
 * the client tests. Call {@link #install()} before the clients first use
 * {@code PortableRemoteObject}.
 * <p>
 * On a real server, {@code PortableRemoteObject.narrow()} turns an RMI-IIOP
 * stub into the requested interface. Here the "stubs" are Mockito mocks that
 * already implement it, so narrow is a checked cast. It throws
 * ClassCastException for the wrong type, as the real one does. Nothing is
 * ever exported, so the other operations aren't supported.
 */
public class LocalPortableRemoteObject implements PortableRemoteObjectDelegate {

    /** Target interfaces of every narrow() call, so tests can check that clients narrow rather than cast. */
    private static final List<Class<?>> NARROWED_TO = new CopyOnWriteArrayList<Class<?>>();

    /**
     * javax.rmi.PortableRemoteObject reads this property once, when the class is
     * first loaded, to choose its delegate. Without it, it would look for the
     * GlassFish ORB implementation, which isn't on the test classpath.
     */
    public static void install() {
        System.setProperty("javax.rmi.CORBA.PortableRemoteObjectClass", LocalPortableRemoteObject.class.getName());
    }

    /** The interfaces narrowed to since the last {@link #reset()}, in call order. */
    public static List<Class<?>> narrowedTo() {
        return NARROWED_TO;
    }

    public static void reset() {
        NARROWED_TO.clear();
    }

    @Override
    public Object narrow(Object narrowFrom, @SuppressWarnings("rawtypes") Class narrowTo) {
        NARROWED_TO.add(narrowTo);
        if (narrowFrom == null) {
            return null;
        }
        if (!narrowTo.isInstance(narrowFrom)) {
            throw new ClassCastException(narrowFrom.getClass().getName() + " cannot be narrowed to "
                    + narrowTo.getName());
        }
        return narrowFrom;
    }

    @Override
    public void exportObject(Remote obj) {
        throw new UnsupportedOperationException("exportObject");
    }

    @Override
    public Remote toStub(Remote obj) {
        throw new UnsupportedOperationException("toStub");
    }

    @Override
    public void unexportObject(Remote obj) {
        throw new UnsupportedOperationException("unexportObject");
    }

    @Override
    public void connect(Remote target, Remote source) {
        throw new UnsupportedOperationException("connect");
    }
}
