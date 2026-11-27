package com.example.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for the {@link PriceChange} message and its JSON form. */
class PriceChangeTest {

    @Test
    void newMessagesHaveAnIdAndAttemptZero() {
        PriceChange m = PriceChange.of("MG-002", 10);
        assertNotNull(m.getId());
        assertEquals("MG-002", m.getSku());
        assertEquals(10, m.getPercent());
        assertEquals(0, m.getAttempt());
        assertNotEquals(m.getId(), PriceChange.of("MG-002", 10).getId());
    }

    @Test
    void nextAttemptKeepsTheIdAndCounts() {
        PriceChange m = PriceChange.of("MG-002", 10);
        PriceChange next = m.nextAttempt().nextAttempt();
        assertEquals(m.getId(), next.getId());
        assertEquals(2, next.getAttempt());
        assertEquals(0, m.getAttempt());
    }

    @Test
    void roundTripsThroughJson() {
        PriceChange m = PriceChange.of("MG-002", 10).nextAttempt();
        assertEquals(m, PriceChange.fromJson(m.toJson()));
        assertEquals(m.hashCode(), PriceChange.fromJson(m.toJson()).hashCode());
    }

    @Test
    void readsHandWrittenJson() {
        PriceChange m = PriceChange.fromJson("{\"id\":\"x\",\"sku\":\"KB-003\",\"percent\":25,\"attempt\":1}");
        assertEquals("PriceChange[x, KB-003, 25%, attempt 1]", m.toString());
    }

    @Test
    void rejectsTextThatIsNotAMessage() {
        assertThrows(IllegalArgumentException.class, () -> PriceChange.fromJson("hello"));
        assertThrows(IllegalArgumentException.class, () -> PriceChange.fromJson("{\"percent\":\"lots\"}"));
        assertThrows(IllegalArgumentException.class, () -> PriceChange.fromJson("{\"colour\":\"red\"}"));
        assertThrows(IllegalArgumentException.class, () -> PriceChange.fromJson(null));
    }

    @Test
    void equality() {
        PriceChange m = PriceChange.fromJson("{\"id\":\"x\",\"sku\":\"A\",\"percent\":5,\"attempt\":0}");
        assertEquals(m, m);
        assertNotEquals(m, "x");
        assertNotEquals(m, m.nextAttempt());
        assertNotEquals(m, PriceChange.fromJson("{\"id\":\"x\",\"sku\":\"B\",\"percent\":5,\"attempt\":0}"));
        assertNotEquals(m, PriceChange.fromJson("{\"id\":\"x\",\"sku\":\"A\",\"percent\":6,\"attempt\":0}"));
        assertNotEquals(m, PriceChange.fromJson("{\"id\":\"y\",\"sku\":\"A\",\"percent\":5,\"attempt\":0}"));
    }
}
