package com.example.ejb.pricing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ejb.product.ProductLocal;
import com.example.ejb.product.ProductLocalHome;
import com.example.ejb.testsupport.InMemoryJndi;
import java.math.BigDecimal;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.MessageDrivenContext;
import javax.ejb.ObjectNotFoundException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.TextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for the message-driven {@link PriceChangeBean}. The Product home
 * is a mock bound in the in-memory JNDI, where ejbCreate() looks it up. The
 * tests check whether each kind of message leads to an update, a discard or a
 * rollback (redelivery).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // JMS header getters are stubbed for every test
class PriceChangeBeanTest {

    @Mock private ProductLocalHome productHome;
    @Mock private ProductLocal product;
    @Mock private MessageDrivenContext mdbContext;
    @Mock private MapMessage message;

    private PriceChangeBean mdb;

    @BeforeEach
    void createBean() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/ejb/Product", productHome);

        when(message.getJMSMessageID()).thenReturn("ID:test-1");
        when(product.getPrice()).thenReturn(new BigDecimal("36.00"));

        mdb = new PriceChangeBean();
        mdb.setMessageDrivenContext(mdbContext);
        mdb.ejbCreate();
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void validMessageDiscountsTheProduct() throws Exception {
        givenMessage("BK-001", 10);
        when(productHome.findByPrimaryKey("BK-001")).thenReturn(product);

        mdb.onMessage(message);

        verify(product).applyDiscount(10);
        verify(mdbContext, never()).setRollbackOnly();
    }

    @Test
    void redeliveredMessageIsProcessedNormally() throws Exception {
        givenMessage("BK-001", 5);
        when(message.getJMSRedelivered()).thenReturn(true);
        when(productHome.findByPrimaryKey("BK-001")).thenReturn(product);

        mdb.onMessage(message);

        verify(product).applyDiscount(5);
    }

    @Test
    void unknownProductIsDiscardedWithoutRollback() throws Exception {
        givenMessage("NOPE", 10);
        when(productHome.findByPrimaryKey("NOPE")).thenThrow(new ObjectNotFoundException("NOPE"));

        mdb.onMessage(message);

        // Rolling back would redeliver a message that can never succeed.
        verify(mdbContext, never()).setRollbackOnly();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100, 150})
    void invalidDiscountIsDiscardedWithoutTouchingTheEntity(int percent) throws Exception {
        givenMessage("BK-001", percent);

        mdb.onMessage(message);

        verify(productHome, never()).findByPrimaryKey(anyString());
        verify(mdbContext, never()).setRollbackOnly();
    }

    @Test
    void unsupportedMessageTypeIsIgnored() {
        TextMessage text = mock(TextMessage.class);

        mdb.onMessage(text);

        verifyNoInteractions(productHome);
        verify(mdbContext, never()).setRollbackOnly();
    }

    @Test
    void finderFailureRollsBackForRedelivery() throws Exception {
        givenMessage("BK-001", 10);
        when(productHome.findByPrimaryKey("BK-001")).thenThrow(new FinderException("database down"));

        mdb.onMessage(message);

        verify(mdbContext).setRollbackOnly();
        verify(product, never()).applyDiscount(anyInt());
    }

    @Test
    void unreadableMessageRollsBack() throws Exception {
        when(message.getString("sku")).thenThrow(new JMSException("corrupt"));

        mdb.onMessage(message);

        verify(mdbContext).setRollbackOnly();
        verifyNoInteractions(productHome);
    }

    @Test
    void ejbCreateFailsWhenProductHomeIsNotBound() {
        InMemoryJndi.install(); // empty

        PriceChangeBean bean = new PriceChangeBean();
        bean.setMessageDrivenContext(mdbContext);
        assertThrows(EJBException.class, bean::ejbCreate);
    }

    private void givenMessage(String sku, int percent) throws JMSException {
        when(message.getString("sku")).thenReturn(sku);
        when(message.getInt("percent")).thenReturn(percent);
    }
}
