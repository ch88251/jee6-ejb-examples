package com.example.client.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/** Captures System.out while a client's main() runs, so tests can check what it prints. */
public final class ConsoleCapture implements AutoCloseable {

    private final PrintStream original = System.out;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public ConsoleCapture() {
        try {
            System.setOut(new PrintStream(buffer, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public String output() {
        try {
            return buffer.toString("UTF-8").replace("\r\n", "\n");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void close() {
        System.setOut(original);
    }
}
