package com.example.web;

import static com.example.web.Html.escape;

import com.example.core.product.DuplicateProductException;
import com.example.core.product.Product;
import com.example.core.product.ProductCatalog;
import com.example.core.product.ProductNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web client for the product catalog, stored in Redis.
 * <p>
 * Each catalog call is atomic on its own. The old version wrapped each request
 * in a JTA {@code UserTransaction} around the CMP entity calls; there is no
 * transaction manager any more. The page is still built in memory first, so a
 * Redis failure halfway through gives an error, not half a page.
 */
public class ProductServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private transient ProductCatalog catalog;

    @Override
    public void init() throws ServletException {
        try {
            catalog = AppContextListener.service(getServletContext(), ProductCatalog.class);
        } catch (IllegalStateException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        respond(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        respond(req, resp, req.getParameter("action"));
    }

    private void respond(HttpServletRequest req, HttpServletResponse resp, String action) throws IOException {
        StringWriter page = new StringWriter();
        String message = action == null ? null : perform(action, req);
        render(req, new PrintWriter(page), message);
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write(page.toString());
    }

    /** Runs one action and returns a message for the user. */
    private String perform(String action, HttpServletRequest req) {
        String sku = req.getParameter("sku");
        try {
            if ("create".equals(action)) {
                Product p = catalog.create(sku, req.getParameter("name"), decimal(req.getParameter("price")));
                return "Created " + p.getSku();
            } else if ("discount".equals(action)) {
                int percent = Integer.parseInt(String.valueOf(req.getParameter("percent")).trim());
                if (percent <= 0 || percent >= 100) {
                    return "Discount must be between 1 and 99 percent.";
                }
                Product p = catalog.applyDiscount(sku, percent);
                return "New price for " + p.getSku() + ": " + p.getPrice();
            } else if ("delete".equals(action)) {
                return catalog.delete(sku) ? "Deleted " + sku : "No product with SKU " + sku;
            } else if ("sample".equals(action)) {
                return loadSampleData();
            }
            return null;
        } catch (DuplicateProductException e) {
            return e.getMessage();
        } catch (ProductNotFoundException e) {
            return e.getMessage();
        } catch (NumberFormatException e) {
            return "Please enter a valid number.";
        } catch (IllegalArgumentException e) {
            return "Could not create product: " + e.getMessage();
        }
    }

    private String loadSampleData() {
        String[][] samples = {
            {"BK-001", "Java EE 6 Tutorial", "39.99"},
            {"MG-002", "Coffee mug", "9.50"},
            {"KB-003", "Mechanical keyboard", "129.00"},
            {"HD-004", "Noise-cancelling headphones", "249.00"},
        };
        int created = 0;
        for (String[] s : samples) {
            try {
                catalog.create(s[0], s[1], new BigDecimal(s[2]));
                created++;
            } catch (DuplicateProductException alreadyThere) {
                // keep the existing product
            }
        }
        return "Created " + created + " sample product(s).";
    }

    private void render(HttpServletRequest req, PrintWriter out, String message) {
        String self = req.getContextPath() + "/products";
        String max = req.getParameter("maxPrice");
        boolean filtered = max != null && !max.trim().isEmpty();
        List<Product> products;
        try {
            products = filtered ? catalog.findByMaxPrice(decimal(max)) : catalog.findAll();
        } catch (NumberFormatException e) {
            products = catalog.findAll();
            filtered = false;
            message = "Not a valid maximum price: " + max;
        }

        out.println("<!DOCTYPE html><html><head><title>Product Catalog</title></head><body>");
        out.println("<h1>Product Catalog (stored in Redis)</h1>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }
        out.println("<p>Average price: " + catalog.getAveragePrice() + "</p>");

        out.println("<form method='get' action='" + self + "'>Max price: <input name='maxPrice' value='"
                + (filtered ? escape(max) : "") + "'/> <button>Filter</button> "
                + "<a href='" + self + "'>Show all</a></form>");

        out.println("<table border='1' cellpadding='4'><tr><th>SKU</th><th>Name</th><th>Price</th><th></th></tr>");
        for (Product p : products) {
            String sku = escape(p.getSku());
            out.println("<tr><td>" + sku + "</td><td>" + escape(p.getName()) + "</td><td>" + p.getPrice() + "</td><td>"
                    + "<form style='display:inline' method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='discount'/><input type='hidden' name='sku' value='" + sku + "'/>"
                    + "<input name='percent' size='3' value='10'/>% <button>Discount</button></form> "
                    + "<form style='display:inline' method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='delete'/><input type='hidden' name='sku' value='" + sku + "'/>"
                    + "<button>Delete</button></form></td></tr>");
        }
        out.println("</table>");

        out.println("<h2>New product</h2><form method='post' action='" + self + "'>"
                + "<input type='hidden' name='action' value='create'/>"
                + "SKU <input name='sku' size='8'/> Name <input name='name'/> Price <input name='price' size='8'/> "
                + "<button>Create</button></form>");
        out.println("<form method='post' action='" + self + "'><input type='hidden' name='action' value='sample'/>"
                + "<button>Load sample products</button></form>");
        out.println("<p><a href='" + req.getContextPath() + "/'>Back</a></p></body></html>");
    }

    private static BigDecimal decimal(String value) {
        if (value == null) {
            throw new NumberFormatException("missing value");
        }
        return new BigDecimal(value.trim());
    }
}
