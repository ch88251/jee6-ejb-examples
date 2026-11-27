package com.example.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.core.pricing.PriceChangeHandler.Outcome;
import com.example.core.product.Product;
import com.example.core.product.ProductCatalog;
import com.example.core.product.ProductNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link PriceChangeHandler}: applied, discarded (poison) and retry outcomes. */
@ExtendWith(MockitoExtension.class)
class PriceChangeHandlerTest {

    @Mock private ProductCatalog catalog;

    private PriceChangeHandler handler() {
        return new PriceChangeHandler(catalog);
    }

    @Test
    void appliesAValidDiscount() throws Exception {
        when(catalog.applyDiscount("MG-002", 10)).thenReturn(Product.of("MG-002", "Mug", new BigDecimal("8.55")));

        assertEquals(Outcome.APPLIED, handler().handle(PriceChange.of("MG-002", 10)));
    }

    @Test
    void discardsAnInvalidPercentWithoutTouchingTheCatalog() throws Exception {
        assertEquals(Outcome.DISCARDED, handler().handle(PriceChange.of("MG-002", 0)));
        assertEquals(Outcome.DISCARDED, handler().handle(PriceChange.of("MG-002", 100)));
        verify(catalog, never()).applyDiscount(anyString(), anyInt());
    }

    @Test
    void discardsAnUnknownSku() throws Exception {
        when(catalog.applyDiscount("NOPE", 10)).thenThrow(new ProductNotFoundException("NOPE"));

        assertEquals(Outcome.DISCARDED, handler().handle(PriceChange.of("NOPE", 10)));
    }

    @Test
    void retriesWhenTheCatalogFails() throws Exception {
        when(catalog.applyDiscount("MG-002", 10)).thenThrow(new IllegalStateException("Redis is down"));

        assertEquals(Outcome.RETRY, handler().handle(PriceChange.of("MG-002", 10).nextAttempt()));
    }

    @Test
    void requiresACatalog() {
        assertThrows(NullPointerException.class, () -> new PriceChangeHandler(null));
    }
}
