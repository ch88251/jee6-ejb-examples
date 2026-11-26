package com.example.web;

import com.example.ejb.product.ProductLocal;
import com.example.ejb.product.ProductLocalHome;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.DuplicateKeyException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Status;
import javax.transaction.UserTransaction;

/**
 * Web client for the EJB 2.x CMP Product entity.
 * <p>
 * Each request runs in one {@link UserTransaction}. Every entity call then
 * joins it (the trans-attribute is Required), so the finder results and the
 * getters run against one consistent view of the data. All changes are
 * written when the transaction commits. Without it, each entity call would be
 * its own short transaction.
 */
public class ProductServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String HOME_JNDI = "java:comp/env/ejb/Product";

    private transient ProductLocalHome home;

    @Override
    public void init() throws ServletException {
        try {
            home = (ProductLocalHome) new InitialContext().lookup(HOME_JNDI);
        } catch (NamingException e) {
            throw new ServletException("Cannot look up " + HOME_JNDI, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        inTransaction(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        inTransaction(req, resp, req.getParameter("action"));
    }

    private void inTransaction(HttpServletRequest req, HttpServletResponse resp, String action)
            throws ServletException, IOException {
        StringWriter page = new StringWriter();
        UserTransaction utx = userTransaction();
        try {
            utx.begin();
            String message = action == null ? null : perform(action, req);
            render(req, new PrintWriter(page), message);
            if (utx.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
                utx.rollback();
            } else {
                utx.commit(); // the container writes changed cmp-fields now
            }
        } catch (Exception e) {
            rollbackQuietly(utx);
            throw new ServletException("Product request failed", e);
        }
        // Send the page only after a successful commit.
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write(page.toString());
    }

    /** Runs one action and returns a message for the user. */
    private String perform(String action, HttpServletRequest req) throws FinderException, RemoveException {
        String sku = req.getParameter("sku");
        try {
            if ("create".equals(action)) {
                ProductLocal p = home.create(sku, req.getParameter("name"), decimal(req.getParameter("price")));
                return "Created " + p.getSku();
            } else if ("discount".equals(action)) {
                int percent = Integer.parseInt(String.valueOf(req.getParameter("percent")).trim());
                if (percent <= 0 || percent >= 100) {
                    // Checked here: the bean would throw an EJBException, which
                    // rolls back the whole transaction.
                    return "Discount must be between 1 and 99 percent.";
                }
                ProductLocal p = home.findByPrimaryKey(sku);
                p.applyDiscount(percent);
                return "New price for " + sku + ": " + p.getPrice();
            } else if ("delete".equals(action)) {
                home.findByPrimaryKey(sku).remove(); // container DELETEs the row
                return "Deleted " + sku;
            } else if ("sample".equals(action)) {
                return loadSampleData();
            }
            return null;
        } catch (DuplicateKeyException e) {
            return "A product with SKU " + sku + " already exists.";
        } catch (CreateException e) {
            return "Could not create product: " + e.getMessage();
        } catch (ObjectNotFoundException e) {
            return "No product with SKU " + sku;
        } catch (NumberFormatException e) {
            return "Please enter a valid number.";
        }
    }

    private String loadSampleData() throws FinderException {
        String[][] samples = {
            {"BK-001", "Java EE 6 Tutorial", "39.99"},
            {"MG-002", "Coffee mug", "9.50"},
            {"KB-003", "Mechanical keyboard", "129.00"},
            {"HD-004", "Noise-cancelling headphones", "249.00"},
        };
        int created = 0;
        for (String[] s : samples) {
            try {
                home.findByPrimaryKey(s[0]);
            } catch (ObjectNotFoundException notThereYet) {
                try {
                    home.create(s[0], s[1], new BigDecimal(s[2]));
                    created++;
                } catch (CreateException e) {
                    return "Could not create " + s[0] + ": " + e.getMessage();
                }
            }
        }
        return "Created " + created + " sample product(s).";
    }

    private void render(HttpServletRequest req, PrintWriter out, String message) throws FinderException {
        String self = req.getContextPath() + "/products";
        String max = req.getParameter("maxPrice");
        boolean filtered = max != null && !max.trim().isEmpty();
        Collection<ProductLocal> products;
        try {
            products = filtered ? home.findByMaxPrice(decimal(max)) : home.findAll();
        } catch (NumberFormatException e) {
            products = home.findAll();
            filtered = false;
            message = "Not a valid maximum price: " + max;
        }

        out.println("<!DOCTYPE html><html><head><title>EJB 2.x CMP Entity</title></head><body>");
        out.println("<h1>EJB 2.x CMP Entity Bean Demo</h1>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }
        out.println("<p>Average price (home method): " + home.getAveragePrice() + "</p>");

        out.println("<form method='get' action='" + self + "'>Max price: <input name='maxPrice' value='"
                + (filtered ? escape(max) : "") + "'/> <button>Filter (findByMaxPrice)</button> "
                + "<a href='" + self + "'>Show all (findAll)</a></form>");

        out.println("<table border='1' cellpadding='4'><tr><th>SKU</th><th>Name</th><th>Price</th><th></th></tr>");
        for (ProductLocal p : products) {
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

    private static UserTransaction userTransaction() throws ServletException {
        try {
            return (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        } catch (NamingException e) {
            throw new ServletException(e);
        }
    }

    private static void rollbackQuietly(UserTransaction utx) {
        try {
            if (utx.getStatus() != Status.STATUS_NO_TRANSACTION) {
                utx.rollback();
            }
        } catch (Exception ignored) {
            // the original exception is the one worth reporting
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}
