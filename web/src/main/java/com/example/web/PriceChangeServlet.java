package com.example.web;

import static com.example.web.Html.escape;

import com.example.core.pricing.PriceChangeQueue;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Sends price change messages to the Redis queue that the price change worker
 * consumes. This replaces sending a JMS message to the message-driven bean's queue.
 * <p>
 * The servlet doesn't wait for the result: the worker processes the message
 * asynchronously, a moment later. Reload the products page to see the new
 * price, and check the server log for the worker's output.
 */
public class PriceChangeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private transient PriceChangeQueue queue;

    @Override
    public void init() throws ServletException {
        try {
            queue = AppContextListener.service(getServletContext(), PriceChangeQueue.class);
        } catch (IllegalStateException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sku = req.getParameter("sku");
        String percentParam = req.getParameter("percent");
        String message;
        if (sku == null || sku.trim().isEmpty()) {
            message = "Please enter a SKU.";
        } else {
            try {
                int percent = Integer.parseInt(String.valueOf(percentParam).trim());
                String normalizedSku = sku.trim().toUpperCase();
                String id = queue.send(normalizedSku, percent);
                message = "Queued message " + id + ": " + percent + "% off " + normalizedSku
                        + ". The worker processes it asynchronously.";
            } catch (NumberFormatException e) {
                message = "Please enter a whole-number percentage.";
            }
        }
        render(req, resp, message);
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        String ctx = req.getContextPath();
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>Asynchronous Price Changes</title></head><body>");
        out.println("<h1>Asynchronous Price Changes (Redis queue)</h1>");
        out.println("<p>Pushes a message onto the Redis list <code>jee6-ejb-examples:pricing:queue</code>. "
                + "A background worker applies the discount to the product catalog.</p>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }
        out.println("<form method='post' action='" + ctx + "/pricing'>"
                + "SKU <input name='sku' size='8' value='" + escape(req.getParameter("sku")) + "'/> "
                + "Discount <input name='percent' size='3' value='10'/>% "
                + "<button>Send message</button></form>");
        out.println("<p>Try an unknown SKU or a discount of 0 or 150%: the worker logs the message and "
                + "discards it instead of retrying.</p>");
        out.println("<p><a href='" + ctx + "/products'>View products</a> | <a href='" + ctx + "/'>Back</a></p>");
        out.println("</body></html>");
    }
}
