package com.example.client;

import com.example.ejb.converter.CurrencyConverter;
import com.example.ejb.converter.CurrencyConverterHome;
import com.example.ejb.converter.UnsupportedCurrencyException;
import java.math.BigDecimal;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

/**
 * Standalone client for the EJB 2.x stateless converter, using its remote view.
 * <p>
 * JNDI and classpath setup is the same as for {@link ShoppingCartRemoteClient}.
 * Set the home's JNDI name with {@code -Dconverter.jndi=...}.
 * <p>
 * Arguments (optional): {@code <amount> <from> <to>}, e.g. {@code 250 GBP JPY}.
 */
public class CurrencyConverterRemoteClient {

    static final String DEFAULT_JNDI_NAME =
            "java:global/jee6-ejb-examples/jee6-ejb-examples-ejb/CurrencyConverterEJB!com.example.ejb.converter.CurrencyConverterHome";

    public static void main(String[] args) throws Exception {
        String jndiName = System.getProperty("converter.jndi", DEFAULT_JNDI_NAME);

        CurrencyConverterHome home = lookupHome(jndiName);

        // For a stateless bean, create() takes no arguments and just returns a
        // reference. No client-specific instance is set up.
        CurrencyConverter converter = home.create();
        System.out.println("Supported currencies: " + converter.getSupportedCurrencies());
        System.out.println("Commission: " + converter.getCommissionPercent() + "%");

        if (args.length == 3) {
            convert(converter, new BigDecimal(args[0]), args[1], args[2]);
        } else {
            convert(converter, new BigDecimal("100"), "USD", "EUR");
            convert(converter, new BigDecimal("250"), "GBP", "JPY");
            convert(converter, new BigDecimal("1000"), "JPY", "CAD");
        }

        // An application exception reaches the client unwrapped.
        try {
            converter.convert(BigDecimal.ONE, "USD", "XYZ");
        } catch (UnsupportedCurrencyException e) {
            System.out.println("Expected application exception: " + e.getMessage());
        }

        // Each call above may have gone to a different pooled instance; compare
        // the [CurrencyConverterBean@...] lines in the server log.
        //
        // For a stateless bean, two references from the same home are always
        // "identical": every instance is interchangeable.
        CurrencyConverter another = home.create();
        System.out.println("Two create() results identical = " + converter.isIdentical(another));

        // remove() only discards the client's reference. The container keeps the
        // instances in its pool, and ejbRemove() is NOT called.
        converter.remove();
        System.out.println("Reference removed.");
    }

    private static void convert(CurrencyConverter converter, BigDecimal amount, String from, String to)
            throws Exception {
        BigDecimal result = converter.convert(amount, from, to);
        System.out.println(amount + " " + from + " = " + result.toPlainString() + " " + to);
    }

    static CurrencyConverterHome lookupHome(String jndiName) throws NamingException {
        InitialContext ctx = new InitialContext();
        try {
            System.out.println("Looking up " + jndiName);
            Object ref = ctx.lookup(jndiName);
            return (CurrencyConverterHome) PortableRemoteObject.narrow(ref, CurrencyConverterHome.class);
        } finally {
            ctx.close();
        }
    }
}
