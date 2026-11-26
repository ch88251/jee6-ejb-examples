package com.example.web;

import com.example.ejb.converter.CurrencyConverterLocal;
import com.example.ejb.converter.CurrencyConverterLocalHome;
import com.example.ejb.converter.UnsupportedCurrencyException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import javax.ejb.CreateException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web client for the EJB 2.x stateless converter.
 * <p>
 * Compare with {@link ShoppingCartServlet}: nothing is stored in the HTTP
 * session. The home is looked up once and cached, which is safe because homes
 * are thread-safe. Each request calls {@code create()}, which is cheap for a
 * stateless bean because it only returns a reference; pooled instances do
 * the work.
 */
public class CurrencyConverterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String HOME_JNDI = "java:comp/env/ejb/CurrencyConverter";

    private transient CurrencyConverterLocalHome home;

    @Override
    public void init() throws ServletException {
        try {
            home = (CurrencyConverterLocalHome) new InitialContext().lookup(HOME_JNDI);
        } catch (NamingException e) {
            throw new ServletException("Cannot look up " + HOME_JNDI, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        render(req, resp, newConverter(), "100", "USD", "EUR", null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String amount = req.getParameter("amount");
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        CurrencyConverterLocal converter = newConverter();
        String message;
        if (amount == null || amount.trim().isEmpty()) {
            render(req, resp, converter, "", from, to, "Please enter an amount.");
            return;
        }
        try {
            BigDecimal result = converter.convert(new BigDecimal(amount.trim()), from, to);
            message = amount.trim() + " " + from + " = " + result.toPlainString() + " " + to;
        } catch (NumberFormatException e) {
            message = "Not a valid amount: " + amount;
        } catch (UnsupportedCurrencyException e) {
            message = e.getMessage();
        }
        render(req, resp, converter, amount, from, to, message);
    }

    private CurrencyConverterLocal newConverter() throws ServletException {
        try {
            return home.create();
        } catch (CreateException e) {
            throw new ServletException(e);
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, CurrencyConverterLocal converter,
                        String amount, String from, String to, String message) throws IOException {
        List<String> currencies = converter.getSupportedCurrencies();

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>EJB 2.x Stateless Converter</title></head><body>");
        out.println("<h1>EJB 2.x Stateless Session Bean Demo</h1>");
        out.println("<p>Commission: " + converter.getCommissionPercent() + "% (set by an env-entry in ejb-jar.xml)</p>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }
        out.println("<form method='post' action='" + req.getContextPath() + "/converter'>"
                + "<input name='amount' value='" + escape(amount) + "'/> "
                + select("from", currencies, from) + " to " + select("to", currencies, to)
                + " <button>Convert</button></form>");
        out.println("<p><a href='" + req.getContextPath() + "/'>Back</a></p>");
        out.println("</body></html>");
    }

    private static String select(String name, List<String> options, String selected) {
        StringBuilder sb = new StringBuilder("<select name='").append(name).append("'>");
        for (String option : options) {
            sb.append("<option").append(option.equals(selected) ? " selected" : "")
              .append('>').append(option).append("</option>");
        }
        return sb.append("</select>").toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}
