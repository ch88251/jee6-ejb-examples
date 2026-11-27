package com.example.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.core.product.DuplicateProductException;
import com.example.core.product.Product;
import com.example.core.product.ProductCatalog;
import com.example.core.product.ProductNotFoundException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import javax.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link ProductServlet} with the catalog mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // findAll/average are stubbed for every render
class ProductServletTest {

    private static final Product MUG = Product.of("MG-002", "Coffee mug", new BigDecimal("9.50"));
    private static final Product BOOK = Product.of("BK-001", "Java EE 6 Tutorial", new BigDecimal("39.99"));

    @Mock private ProductCatalog catalog;

    private ProductServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        servlet = ServletSetup.init(new ProductServlet(), ProductCatalog.class, catalog);
        http = new HttpFixture();
        when(catalog.findAll()).thenReturn(Arrays.asList(BOOK, MUG));
        when(catalog.getAveragePrice()).thenReturn(new BigDecimal("24.75"));
    }

    @Test
    void initFailsWithoutTheCatalog() {
        assertThrows(ServletException.class, () -> ServletSetup.init(new ProductServlet(), String.class, "unrelated"));
    }

    @Test
    void getListsProductsAndTheAverage() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("<td>BK-001</td><td>Java EE 6 Tutorial</td><td>39.99</td>"));
        assertTrue(http.html().contains("<td>MG-002</td><td>Coffee mug</td><td>9.50</td>"));
        assertTrue(http.html().contains("Average price: 24.75"));
    }

    @Test
    void maxPriceFilterUsesFindByMaxPrice() throws Exception {
        when(catalog.findByMaxPrice(new BigDecimal("10"))).thenReturn(Collections.singletonList(MUG));

        servlet.doGet(http.param("maxPrice", " 10 ").request, http.response);

        assertTrue(http.html().contains("MG-002"));
        assertFalse(http.html().contains("BK-001"));
        assertTrue(http.html().contains("value=' 10 '"));
    }

    @Test
    void invalidMaxPriceFallsBackToFindAll() throws Exception {
        servlet.doGet(http.param("maxPrice", "cheap").request, http.response);

        assertTrue(http.html().contains("Not a valid maximum price: cheap"));
        assertTrue(http.html().contains("BK-001"));
    }

    @Test
    void createStoresTheProduct() throws Exception {
        when(catalog.create("hd-004", "Headphones", new BigDecimal("249"))).thenReturn(
                Product.of("HD-004", "Headphones", new BigDecimal("249")));

        servlet.doPost(http.param("action", "create").param("sku", "hd-004").param("name", "Headphones")
                .param("price", "249").request, http.response);

        assertTrue(http.html().contains("Created HD-004"));
    }

    @Test
    void duplicateSkuShowsMessage() throws Exception {
        when(catalog.create("MG-002", "x", BigDecimal.ONE)).thenThrow(new DuplicateProductException("MG-002"));

        servlet.doPost(http.param("action", "create").param("sku", "MG-002").param("name", "x")
                .param("price", "1").request, http.response);

        assertTrue(http.html().contains("A product with SKU MG-002 already exists."));
    }

    @Test
    void createValidationErrorShowsMessage() throws Exception {
        when(catalog.create("", "x", BigDecimal.ONE)).thenThrow(new IllegalArgumentException("SKU is required"));

        servlet.doPost(http.param("action", "create").param("sku", "").param("name", "x")
                .param("price", "1").request, http.response);

        assertTrue(http.html().contains("Could not create product: SKU is required"));
    }

    @Test
    void createWithInvalidPriceShowsMessage() throws Exception {
        servlet.doPost(http.param("action", "create").param("sku", "S").param("name", "x").request, http.response);

        assertTrue(http.html().contains("Please enter a valid number."));
        verify(catalog, never()).create(anyString(), anyString(), any());
    }

    @Test
    void discountUpdatesTheProduct() throws Exception {
        when(catalog.applyDiscount("MG-002", 10)).thenReturn(MUG.withDiscount(10));

        servlet.doPost(http.param("action", "discount").param("sku", "MG-002").param("percent", "10").request,
                http.response);

        assertTrue(http.html().contains("New price for MG-002: 8.55"));
    }

    @Test
    void discountRangeIsCheckedBeforeCallingTheCatalog() throws Exception {
        servlet.doPost(http.param("action", "discount").param("sku", "MG-002").param("percent", "100").request,
                http.response);
        HttpFixture nonNumeric = new HttpFixture().param("action", "discount").param("sku", "MG-002")
                .param("percent", "lots");
        servlet.doPost(nonNumeric.request, nonNumeric.response);

        assertTrue(http.html().contains("Discount must be between 1 and 99 percent."));
        assertTrue(nonNumeric.html().contains("Please enter a valid number."));
        verify(catalog, never()).applyDiscount(anyString(), anyInt());
    }

    @Test
    void unknownSkuShowsMessage() throws Exception {
        when(catalog.applyDiscount("NOPE", 10)).thenThrow(new ProductNotFoundException("NOPE"));

        servlet.doPost(http.param("action", "discount").param("sku", "NOPE").param("percent", "10").request,
                http.response);

        assertTrue(http.html().contains("No product with SKU NOPE"));
    }

    @Test
    void deleteRemovesTheProduct() throws Exception {
        when(catalog.delete("MG-002")).thenReturn(true);

        servlet.doPost(http.param("action", "delete").param("sku", "MG-002").request, http.response);
        HttpFixture missing = new HttpFixture().param("action", "delete").param("sku", "NOPE");
        servlet.doPost(missing.request, missing.response);

        assertTrue(http.html().contains("Deleted MG-002"));
        assertTrue(missing.html().contains("No product with SKU NOPE"));
    }

    @Test
    void sampleDataCreatesOnlyMissingProducts() throws Exception {
        when(catalog.create("MG-002", "Coffee mug", new BigDecimal("9.50")))
                .thenThrow(new DuplicateProductException("MG-002"));

        servlet.doPost(http.param("action", "sample").request, http.response);

        verify(catalog, times(4)).create(anyString(), anyString(), any());
        assertTrue(http.html().contains("Created 3 sample product(s)."));
    }

    @Test
    void unknownActionJustRendersThePage() throws Exception {
        servlet.doPost(http.param("action", "dance").request, http.response);

        assertTrue(http.html().contains("BK-001"));
        assertFalse(http.html().contains("<strong>"));
    }

    @Test
    void aCatalogFailureSendsNoPartialPage() {
        when(catalog.getAveragePrice()).thenThrow(new IllegalStateException("Redis is down"));

        assertThrows(IllegalStateException.class, () -> servlet.doGet(http.request, http.response));
        assertEquals("", http.html());
    }

    @Test
    void productDataIsHtmlEscaped() throws Exception {
        when(catalog.findAll()).thenReturn(Collections.singletonList(
                Product.of("<S>", "<script>x</script>", BigDecimal.ONE)));

        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("&lt;script&gt;x&lt;/script&gt;"));
        assertTrue(http.html().contains("value='&lt;S&gt;'"));
        assertFalse(http.html().contains("<script>"));
    }
}
