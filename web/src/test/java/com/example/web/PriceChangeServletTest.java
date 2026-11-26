package com.example.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ejb.testsupport.InMemoryJndi;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link PriceChangeServlet}: the JMS resources looked up in
 * init(), the MapMessage it sends for the MDB, and the connection always
 * being closed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // the JMS object chain is stubbed for every test
class PriceChangeServletTest {

    @Mock private ConnectionFactory connectionFactory;
    @Mock private Queue queue;
    @Mock private Connection connection;
    @Mock private Session session;
    @Mock private MessageProducer producer;
    @Mock private MapMessage message;
    @Mock private ServletConfig config;

    private PriceChangeServlet servlet;
    private HttpFixture http;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/jms/ConnectionFactory", connectionFactory);
        InMemoryJndi.bind("java:comp/env/jms/PriceChangeQueue", queue);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createProducer(queue)).thenReturn(producer);
        when(session.createMapMessage()).thenReturn(message);
        when(message.getJMSMessageID()).thenReturn("ID:42");

        servlet = new PriceChangeServlet();
        servlet.init(config);
        http = new HttpFixture();
    }

    @AfterEach
    void cleanUp() {
        InMemoryJndi.uninstall();
    }

    @Test
    void initFailsWhenQueueIsNotBound() {
        InMemoryJndi.install();
        InMemoryJndi.bind("java:comp/env/jms/ConnectionFactory", connectionFactory);

        assertThrows(ServletException.class, () -> new PriceChangeServlet().init(config));
    }

    @Test
    void getRendersFormWithoutSending() throws Exception {
        servlet.doGet(http.request, http.response);

        assertTrue(http.html().contains("Send message"));
        verify(connectionFactory, never()).createConnection();
    }

    @Test
    void postSendsMapMessageToTheQueue() throws Exception {
        servlet.doPost(http.param("sku", " bk-001 ").param("percent", " 10 ").request, http.response);

        InOrder order = inOrder(connection, session, message, producer);
        order.verify(connection).createSession(false, Session.AUTO_ACKNOWLEDGE); // not transacted
        order.verify(session).createProducer(queue);
        order.verify(message).setString("sku", "BK-001");                        // trimmed, upper case
        order.verify(message).setInt("percent", 10);
        order.verify(producer).send(message);
        order.verify(connection).close();
        assertTrue(http.html().contains("Queued message ID:42: 10% off BK-001."));
    }

    @Test
    void outOfRangeDiscountIsSentAnywayForTheMdbToDiscard() throws Exception {
        // The servlet doesn't validate the range; PriceChangeBean discards such messages.
        servlet.doPost(http.param("sku", "BK-001").param("percent", "150").request, http.response);

        verify(message).setInt("percent", 150);
        verify(producer).send(message);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingSkuSendsNothing(String sku) throws Exception {
        servlet.doPost(http.param("sku", sku).param("percent", "10").request, http.response);

        assertTrue(http.html().contains("Please enter a SKU."));
        verify(connectionFactory, never()).createConnection();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ten", "10.5", ""})
    void nonIntegerPercentSendsNothing(String percent) throws Exception {
        servlet.doPost(http.param("sku", "BK-001").param("percent", percent).request, http.response);

        assertTrue(http.html().contains("Please enter a whole-number percentage."));
        verify(connectionFactory, never()).createConnection();
    }

    @Test
    void missingPercentSendsNothing() throws Exception {
        servlet.doPost(http.param("sku", "BK-001").request, http.response);

        assertTrue(http.html().contains("Please enter a whole-number percentage."));
        verify(connectionFactory, never()).createConnection();
    }

    @Test
    void sendFailureFailsTheRequestButClosesTheConnection() throws Exception {
        doThrow(new JMSException("broker down")).when(producer).send(message);

        assertThrows(ServletException.class, () ->
                servlet.doPost(http.param("sku", "BK-001").param("percent", "10").request, http.response));

        verify(connection).close();
    }

    @Test
    void echoedSkuIsHtmlEscaped() throws Exception {
        servlet.doPost(http.param("sku", "<i>x</i>").param("percent", "10").request, http.response);

        assertTrue(http.html().contains("&lt;I&gt;X&lt;/I&gt;"));
        assertTrue(http.html().contains("value='&lt;i&gt;x&lt;/i&gt;'"));
    }
}
