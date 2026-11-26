package com.example.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ejb.product.ProductLocal;
import com.example.ejb.product.ProductLocalHome;
import com.example.ejb.testsupport.InMemoryJndi;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import javax.ejb.CreateException;
import javax.ejb.DuplicateKeyException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ProductServlet}: each request wrapped in a
 * UserTransaction, the entity home's finders/create/remove, and the page sent
 * only after a successful commit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // finder/getter stubs are shared by all tests
class ProductServletTest {

    @Mock private ProductLocalHome home;
    @Mock private UserTransaction utx;
    @Mock private ServletConfig config;

    private ProductServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/ejb/Product", home);
        InMemoryJndi.bind("java:comp/UserTransaction", utx);
        when(utx.getStatus()).thenReturn(Status.STATUS_ACTIVE);
        when(home.findAll()).thenReturn(Collections.<ProductLocal>emptyList());
        when(home.getAveragePrice()).thenReturn(new BigDecimal("0.00"));

        servlet = new ProductServlet();
        servlet.init(config);
        http = new HttpFixture();
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void initFailsWhenHomeIsNotBound() {
        InMemoryJndi.install();
        assertThrows(ServletException.class, () -> new ProductServlet().init(config));
    }

    @Test
    void getListsProductsInsideOneTransaction() throws Exception {
        ProductLocal book = product("BK-001", "Java EE 6 Tutorial", "39.99");
        ProductLocal mug = product("MG-002", "Coffee mug", "9.50");
        when(home.findAll()).thenReturn(Arrays.asList(book, mug));
        when(home.getAveragePrice()).thenReturn(new BigDecimal("24.75"));

        servlet.doGet(http.request, http.response);

        InOrder order = inOrder(utx, home, book);
        order.verify(utx).begin();
        order.verify(home).findAll();
        order.verify(book).getSku();       // entity getters run inside the transaction
        order.verify(utx).commit();
        String html = http.html();
        assertTrue(html.contains("<td>BK-001</td><td>Java EE 6 Tutorial</td><td>39.99</td>"));
        assertTrue(html.contains("<td>MG-002</td>"));
        assertTrue(html.contains("Average price (home method): 24.75"));
    }

    @Test
    void maxPriceFilterUsesFinder() throws Exception {
        ProductLocal mug = product("MG-002", "Coffee mug", "9.50");
        when(home.findByMaxPrice(new BigDecimal("50"))).thenReturn(Arrays.asList(mug));

        servlet.doGet(http.param("maxPrice", " 50 ").request, http.response);

        verify(home).findByMaxPrice(new BigDecimal("50"));
        verify(home, never()).findAll();
        assertTrue(http.html().contains("<td>MG-002</td>"));
    }

    @Test
    void invalidMaxPriceFallsBackToFindAll() throws Exception {
        servlet.doGet(http.param("maxPrice", "cheap").request, http.response);

        verify(home).findAll();
        assertTrue(http.html().contains("Not a valid maximum price: cheap"));
    }

    @Test
    void createInsertsEntityBeforeCommit() throws Exception {
        ProductLocal created = product("KB-003", "Keyboard", "129.00");
        when(home.create("KB-003", "Keyboard", new BigDecimal("129.00"))).thenReturn(created);

        servlet.doPost(http.param("action", "create").param("sku", "KB-003").param("name", "Keyboard")
                .param("price", "129.00").request, http.response);

        InOrder order = inOrder(utx, home);
        order.verify(utx).begin();
        order.verify(home).create("KB-003", "Keyboard", new BigDecimal("129.00"));
        order.verify(utx).commit();
        assertTrue(http.html().contains("Created KB-003"));
    }

    @Test
    void duplicateSkuShowsMessage() throws Exception {
        when(home.create(anyString(), anyString(), any())).thenThrow(new DuplicateKeyException());

        servlet.doPost(http.param("action", "create").param("sku", "BK-001").param("name", "x")
                .param("price", "1").request, http.response);

        assertTrue(http.html().contains("A product with SKU BK-001 already exists."));
    }

    @Test
    void createValidationErrorShowsMessage() throws Exception {
        when(home.create(anyString(), anyString(), any())).thenThrow(new CreateException("Name is required"));

        servlet.doPost(http.param("action", "create").param("sku", "X").param("name", "")
                .param("price", "1").request, http.response);

        assertTrue(http.html().contains("Could not create product: Name is required"));
    }

    @Test
    void createWithInvalidPriceShowsMessage() throws Exception {
        servlet.doPost(http.param("action", "create").param("sku", "X").param("name", "x")
                .param("price", "free").request, http.response);

        verify(home, never()).create(anyString(), anyString(), any());
        assertTrue(http.html().contains("Please enter a valid number."));
    }

    @Test
    void discountCallsBusinessMethodOnEntity() throws Exception {
        ProductLocal book = product("BK-001", "Java EE 6 Tutorial", "35.99");
        when(home.findByPrimaryKey("BK-001")).thenReturn(book);

        servlet.doPost(http.param("action", "discount").param("sku", "BK-001").param("percent", "10").request,
                http.response);

        verify(book).applyDiscount(10);
        assertTrue(http.html().contains("New price for BK-001: 35.99"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100", "-5"})
    void outOfRangeDiscountIsRejectedBeforeTouchingTheEntity(String percent) throws Exception {
        servlet.doPost(http.param("action", "discount").param("sku", "BK-001").param("percent", percent).request,
                http.response);

        verify(home, never()).findByPrimaryKey(anyString());
        assertTrue(http.html().contains("Discount must be between 1 and 99 percent."));
        verify(utx).commit();
    }

    @Test
    void nonNumericDiscountShowsMessage() throws Exception {
        servlet.doPost(http.param("action", "discount").param("sku", "BK-001").param("percent", "lots").request,
                http.response);

        assertTrue(http.html().contains("Please enter a valid number."));
    }

    @Test
    void deleteRemovesTheEntity() throws Exception {
        ProductLocal book = product("BK-001", "Java EE 6 Tutorial", "39.99");
        when(home.findByPrimaryKey("BK-001")).thenReturn(book);

        servlet.doPost(http.param("action", "delete").param("sku", "BK-001").request, http.response);

        verify(book).remove();
        assertTrue(http.html().contains("Deleted BK-001"));
    }

    @Test
    void unknownSkuShowsMessage() throws Exception {
        when(home.findByPrimaryKey("NOPE")).thenThrow(new ObjectNotFoundException());

        servlet.doPost(http.param("action", "delete").param("sku", "NOPE").request, http.response);

        assertTrue(http.html().contains("No product with SKU NOPE"));
    }

    @Test
    void sampleDataCreatesOnlyMissingProducts() throws Exception {
        // BK-001 already exists; the other three samples don't.
        ProductLocal existing = product("BK-001", "Java EE 6 Tutorial", "39.99");
        doThrow(new ObjectNotFoundException()).when(home).findByPrimaryKey(anyString());
        doReturn(existing).when(home).findByPrimaryKey("BK-001");

        servlet.doPost(http.param("action", "sample").request, http.response);

        verify(home, never()).create(eq("BK-001"), anyString(), any());
        verify(home, times(3)).create(anyString(), anyString(), any());
        assertTrue(http.html().contains("Created 3 sample product(s)."));
    }

    @Test
    void failureRollsBackAndSendsNoPage() throws Exception {
        when(home.findAll()).thenThrow(new FinderException("database down"));

        assertThrows(ServletException.class, () -> servlet.doGet(http.request, http.response));

        verify(utx).rollback();
        verify(utx, never()).commit();
        verify(http.response, never()).getWriter();
    }

    @Test
    void failedCommitSendsNoPage() throws Exception {
        doThrow(new RollbackException("constraint violated")).when(utx).commit();

        assertThrows(ServletException.class, () -> servlet.doGet(http.request, http.response));

        verify(http.response, never()).getWriter();
    }

    @Test
    void transactionMarkedRollbackOnlyIsRolledBack() throws Exception {
        when(utx.getStatus()).thenReturn(Status.STATUS_MARKED_ROLLBACK);

        servlet.doGet(http.request, http.response);

        verify(utx).rollback();
        verify(utx, never()).commit();
        assertFalse(http.html().isEmpty(), "the page is still sent");
    }

    @Test
    void productDataIsHtmlEscaped() throws Exception {
        ProductLocal tricky = product("X-1", "<b>bold</b> & 'quoted'", "1.00");
        when(home.findAll()).thenReturn(Arrays.asList(tricky));

        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("&lt;b&gt;bold&lt;/b&gt; &amp; &#39;quoted&#39;"));
        assertEquals(-1, http.html().indexOf("<b>bold</b>"));
    }

    private static ProductLocal product(String sku, String name, String price) {
        ProductLocal p = mock(ProductLocal.class);
        when(p.getSku()).thenReturn(sku);
        when(p.getName()).thenReturn(name);
        when(p.getPrice()).thenReturn(new BigDecimal(price));
        return p;
    }
}
