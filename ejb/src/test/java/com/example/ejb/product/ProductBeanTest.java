package com.example.ejb.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the CMP {@link ProductBean}.
 * <p>
 * The bean is abstract: at deploy time the container generates a subclass
 * that implements the cmp-field accessors and the EJB-QL {@code ejbSelect}
 * method. {@link InMemoryProductBean} stands in for that subclass, so the
 * bean's own logic can be tested without a database. The EJB-QL itself can only
 * be tested on a real server.
 */
class ProductBeanTest {

    /** What the container generates: plain fields for the cmp-fields, a canned ejbSelect result. */
    static class InMemoryProductBean extends ProductBean {
        private static final long serialVersionUID = 1L;
        private String sku;
        private String name;
        private BigDecimal price;
        private Collection<BigDecimal> allPrices = new ArrayList<BigDecimal>();

        @Override public String getSku() { return sku; }
        @Override public void setSku(String sku) { this.sku = sku; }
        @Override public String getName() { return name; }
        @Override public void setName(String name) { this.name = name; }
        @Override public BigDecimal getPrice() { return price; }
        @Override public void setPrice(BigDecimal price) { this.price = price; }
        @Override public Collection<BigDecimal> ejbSelectAllPrices() { return allPrices; }
    }

    private InMemoryProductBean bean;
    private EntityContext entityContext;

    @BeforeEach
    void setUp() {
        bean = new InMemoryProductBean();
        entityContext = mock(EntityContext.class);
        bean.setEntityContext(entityContext);
    }

    @Test
    void ejbCreateSetsNormalizedFieldsAndReturnsNullKey() throws Exception {
        // For CMP, ejbCreate returns null; the container takes the key from the primkey-field.
        assertNull(bean.ejbCreate("  bk-001 ", "  Java EE 6 Tutorial ", new BigDecimal("39.9")));
        bean.ejbPostCreate("bk-001", "Java EE 6 Tutorial", new BigDecimal("39.9"));

        assertEquals("BK-001", bean.getSku());
        assertEquals("Java EE 6 Tutorial", bean.getName());
        assertEquals(new BigDecimal("39.90"), bean.getPrice());
    }

    @Test
    void ejbCreateRoundsPriceToCentsHalfEven() throws Exception {
        bean.ejbCreate("A", "A", new BigDecimal("1.005"));
        assertEquals(new BigDecimal("1.00"), bean.getPrice());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NULL,   Name,  1.00",
        "'  ',   Name,  1.00",
        "SKU-1,  NULL,  1.00",
        "SKU-1,  '',    1.00",
        "SKU-1,  Name,  NULL",
        "SKU-1,  Name,  -0.01",
    }, nullValues = "NULL")
    void ejbCreateValidatesInput(String sku, String name, String price) {
        BigDecimal p = price == null ? null : new BigDecimal(price);
        assertThrows(CreateException.class, () -> bean.ejbCreate(sku, name, p));
    }

    @ParameterizedTest(name = "{0} - {1}% = {2}")
    @CsvSource({
        "39.99,  10, 35.99",
        "129.00, 15, 109.65",
        "9.50,   33, 6.36",   // 6.365 rounds half-even to 6.36
        "100.00, 99, 1.00",
        "100.00,  1, 99.00",
    })
    void applyDiscountUpdatesThePriceField(String price, int percent, String expected) throws Exception {
        bean.ejbCreate("SKU", "Thing", new BigDecimal(price));

        bean.applyDiscount(percent);

        assertEquals(new BigDecimal(expected), bean.getPrice());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 100, 150})
    void applyDiscountRejectsOutOfRangePercent(int percent) throws Exception {
        bean.ejbCreate("SKU", "Thing", new BigDecimal("10.00"));

        assertThrows(EJBException.class, () -> bean.applyDiscount(percent));
        assertEquals(new BigDecimal("10.00"), bean.getPrice(), "price unchanged");
    }

    @Test
    void averagePriceHomeMethodUsesSelectMethod() throws Exception {
        bean.allPrices = Arrays.asList(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("25.00"));

        assertEquals(new BigDecimal("18.33"), bean.ejbHomeGetAveragePrice());
    }

    @Test
    void averagePriceOfNoProductsIsZero() throws Exception {
        bean.allPrices = Collections.<BigDecimal>emptyList();

        assertEquals(new BigDecimal("0.00"), bean.ejbHomeGetAveragePrice());
    }

    @Test
    void ejbRemoveUsesTheEntityContextIdentity() {
        when(entityContext.getPrimaryKey()).thenReturn("BK-001");

        bean.ejbRemove();

        verify(entityContext).getPrimaryKey();
    }

    @Test
    void unsetEntityContextIsAccepted() {
        bean.unsetEntityContext();
        // Lifecycle callbacks that CMP leaves empty must still be callable.
        bean.ejbActivate();
        bean.ejbLoad();
        bean.ejbStore();
        bean.ejbPassivate();
    }
}
