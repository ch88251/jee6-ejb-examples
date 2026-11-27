package com.example.core.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/**
 * A price change message: discount product {@code sku} by {@code percent}. It
 * replaces the JMS {@code MapMessage}. It goes on the Redis queue as JSON, for
 * example {@code {"id":"…","sku":"MG-002","percent":10,"attempt":0}}.
 * <p>
 * {@code attempt} counts deliveries that ended in a retry. It plays the part of
 * the JMS redelivered flag and delivery count.
 */
public final class PriceChange {

    private static final ObjectMapper JSON = new ObjectMapper();

    private String id;
    private String sku;
    private int percent;
    private int attempt;

    /** For the JSON mapper only. */
    private PriceChange() {
    }

    private PriceChange(String id, String sku, int percent, int attempt) {
        this.id = id;
        this.sku = sku;
        this.percent = percent;
        this.attempt = attempt;
    }

    /** A new message with a random id and attempt 0. */
    public static PriceChange of(String sku, int percent) {
        return new PriceChange(UUID.randomUUID().toString(), sku, percent, 0);
    }

    /** The same message (same id) for its next delivery attempt. */
    public PriceChange nextAttempt() {
        return new PriceChange(id, sku, percent, attempt + 1);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write " + this, e);
        }
    }

    /** @throws IllegalArgumentException if the text is not a valid price change message */
    public static PriceChange fromJson(String json) {
        try {
            return JSON.readValue(json, PriceChange.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a price change message: " + json, e);
        }
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getPercent() {
        return percent;
    }

    public int getAttempt() {
        return attempt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PriceChange)) {
            return false;
        }
        PriceChange other = (PriceChange) o;
        return percent == other.percent && attempt == other.attempt
                && Objects.equals(id, other.id) && Objects.equals(sku, other.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sku, percent, attempt);
    }

    @Override
    public String toString() {
        return "PriceChange[" + id + ", " + sku + ", " + percent + "%, attempt " + attempt + "]";
    }
}
