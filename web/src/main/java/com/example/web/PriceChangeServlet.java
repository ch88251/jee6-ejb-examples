package com.example.web;

import java.io.IOException;
import java.io.PrintWriter;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Sends price change messages to the queue that the PriceChangeMDB listens on
 * (JMS 1.1 API, as used with EJB 2.x).
 * <p>
 * The servlet doesn't wait for the result: the MDB processes the message
 * asynchronously, a moment later. Reload the products page to see the new
 * price, and check the server log for the MDB's output.
 */
public class PriceChangeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String FACTORY_JNDI = "java:comp/env/jms/ConnectionFactory";
    private static final String QUEUE_JNDI = "java:comp/env/jms/PriceChangeQueue";

    private transient ConnectionFactory connectionFactory;
    private transient Queue queue;

    @Override
    public void init() throws ServletException {
        try {
            InitialContext ctx = new InitialContext();
            connectionFactory = (ConnectionFactory) ctx.lookup(FACTORY_JNDI);
            queue = (Queue) ctx.lookup(QUEUE_JNDI);
        } catch (NamingException e) {
            throw new ServletException("Cannot look up JMS resources", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String sku = req.getParameter("sku");
        String percentParam = req.getParameter("percent");
        String message;
        if (sku == null || sku.trim().isEmpty()) {
            message = "Please enter a SKU.";
        } else {
            try {
                int percent = Integer.parseInt(String.valueOf(percentParam).trim());
                String id = send(sku.trim().toUpperCase(), percent);
                message = "Queued message " + id + ": " + percent + "% off " + sku.trim().toUpperCase()
                        + ". The MDB processes it asynchronously.";
            } catch (NumberFormatException e) {
                message = "Please enter a whole-number percentage.";
            } catch (JMSException e) {
                throw new ServletException("Could not send message", e);
            }
        }
        render(req, resp, message);
    }

    /**
     * Sends one MapMessage and returns its JMS message ID. The servlet isn't in
     * a transaction, so the session is non-transacted and the send takes effect
     * immediately.
     */
    private String send(String sku, int percent) throws JMSException {
        Connection connection = connectionFactory.createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            MapMessage msg = session.createMapMessage();
            msg.setString("sku", sku);
            msg.setInt("percent", percent);
            producer.send(msg);
            return msg.getJMSMessageID();
        } finally {
            connection.close(); // also closes the session and producer
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        String ctx = req.getContextPath();
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>EJB 2.x Message-Driven Bean</title></head><body>");
        out.println("<h1>EJB 2.x Message-Driven Bean Demo</h1>");
        out.println("<p>Sends a JMS MapMessage to <code>jms/PriceChangeQueue</code>. The PriceChangeMDB "
                + "applies the discount to the Product entity in its own transaction.</p>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }
        out.println("<form method='post' action='" + ctx + "/pricing'>"
                + "SKU <input name='sku' size='8' value='" + escape(req.getParameter("sku")) + "'/> "
                + "Discount <input name='percent' size='3' value='10'/>% "
                + "<button>Send message</button></form>");
        out.println("<p>Try an unknown SKU or a discount of 0 or 150%: the MDB logs the message and "
                + "discards it instead of rolling back.</p>");
        out.println("<p><a href='" + ctx + "/products'>View products</a> | <a href='" + ctx + "/'>Back</a></p>");
        out.println("</body></html>");
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}
