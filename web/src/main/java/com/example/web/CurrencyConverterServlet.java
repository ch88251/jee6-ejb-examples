package com.example.web;

import static com.example.web.Html.escape;

import com.example.core.converter.CurrencyConverter;
import com.example.core.converter.UnsupportedCurrencyException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web client for the currency converter.
 * <p>
 * Nothing is stored in the HTTP session. The converter is stateless and
 * thread-safe, so the servlet fetches the one shared instance once, in
 * {@code init()}, and uses it for every request.
 */
public class CurrencyConverterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private transient CurrencyConverter converter;

    @Override
    public void init() throws ServletException {
        try {
            converter = AppContextListener.service(getServletContext(), CurrencyConverter.class);
        } catch (IllegalStateException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(req, resp, "100", "USD", "EUR", null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String amount = req.getParameter("amount");
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        String message;
        if (amount == null || amount.trim().isEmpty()) {
            render(req, resp, "", from, to, "Please enter an amount.");
            return;
        }
        try {
            BigDecimal result = converter.convert(new BigDecimal(amount.trim()), from, to);
            message = amount.trim() + " " + from + " = " + result.toPlainString() + " " + to;
        } catch (NumberFormatException e) {
            message = "Not a valid amount: " + amount;
        } catch (IllegalArgumentException | UnsupportedCurrencyException e) {
            message = e.getMessage();
        }
        render(req, resp, amount, from, to, message);
    }

    private void render(HttpServletRequest req, HttpServletResponse resp,
                        String amount, String from, String to, String message) throws IOException {
        List<String> currencies = converter.getSupportedCurrencies();

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>Currency Converter</title></head><body>");
        out.println("<h1>Currency Converter (stateless service)</h1>");
        out.println("<p>Commission: " + converter.getCommissionPercent()
                + "% (the <code>commissionPercent</code> field of the Redis hash "
                + "<code>jee6-ejb-examples:config</code>)</p>");
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
}
