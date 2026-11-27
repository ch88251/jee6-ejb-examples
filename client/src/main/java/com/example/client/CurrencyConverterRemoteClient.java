package com.example.client;

import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.UnsupportedCurrencyException;
import java.math.BigDecimal;
import org.redisson.api.RedissonClient;

/**
 * Standalone client for the currency converter, calling the web application's
 * {@link CurrencyConverter} through Redisson RemoteService. Connection settings
 * are the same as for {@link ShoppingCartRemoteClient}.
 * <p>
 * Arguments (optional): {@code <amount> <from> <to>}, e.g. {@code 250 GBP JPY}.
 */
public final class CurrencyConverterRemoteClient {

    private CurrencyConverterRemoteClient() {
    }

    public static void main(String[] args) throws Exception {
        RedissonClient redisson = RemoteServices.connect();
        try {
            run(RemoteServices.proxy(redisson, CurrencyConverter.class), args);
        } finally {
            redisson.shutdown();
        }
    }

    static void run(CurrencyConverter converter, String[] args) throws Exception {
        System.out.println("Supported currencies: " + converter.getSupportedCurrencies());
        System.out.println("Commission: " + converter.getCommissionPercent() + "%");

        if (args.length == 3) {
            convert(converter, new BigDecimal(args[0]), args[1], args[2]);
        } else {
            convert(converter, new BigDecimal("100"), "USD", "EUR");
            convert(converter, new BigDecimal("250"), "GBP", "JPY");
            convert(converter, new BigDecimal("1000"), "JPY", "CAD");
        }

        // A checked exception thrown by the service reaches the client as itself.
        try {
            converter.convert(BigDecimal.ONE, "USD", "XYZ");
        } catch (UnsupportedCurrencyException e) {
            System.out.println("Expected application exception: " + e.getMessage());
        }
        // The service is stateless: any server worker may have run each call
        // (compare the [CurrencyConverter] lines in the server log).
    }

    private static void convert(CurrencyConverter converter, BigDecimal amount, String from, String to)
            throws UnsupportedCurrencyException {
        BigDecimal result = converter.convert(amount, from, to);
        System.out.println(amount + " " + from + " = " + result.toPlainString() + " " + to);
    }
}
