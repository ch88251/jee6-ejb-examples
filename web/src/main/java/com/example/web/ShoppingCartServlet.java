package com.example.web;

import com.example.ejb.cart.ItemNotFoundException;
import com.example.ejb.cart.ShoppingCartLocal;
import com.example.ejb.cart.ShoppingCartLocalHome;
import java.io.IOException;
import java.io.PrintWriter;
import javax.ejb.CreateException;
import javax.ejb.NoSuchObjectLocalException;
import javax.ejb.RemoveException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Web client for the EJB 2.x stateful cart.
 * <p>
 * The home is obtained the EJB 2.x way - a JNDI lookup of the
 * {@code ejb-local-ref} declared in web.xml - and the cart's EJBLocalObject is
 * stored in the HTTP session so each browser session talks to its own bean
 * instance.
 */
public class ShoppingCartServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String CART_ATTR = "shoppingCart";
    private static final String HOME_JNDI = "java:comp/env/ejb/ShoppingCart";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        render(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        String message = null;
        HttpSession session = req.getSession();
        ShoppingCartLocal cart = (ShoppingCartLocal) session.getAttribute(CART_ATTR);

        try {
            if ("login".equals(action)) {
                if (cart != null) {
                    cart.remove();
                }
                cart = lookupHome().create(req.getParameter("customer"));
                session.setAttribute(CART_ATTR, cart);
                message = "New cart created for " + cart.getCustomerName();
            } else if (cart == null) {
                message = "Please start a cart first.";
            } else if ("add".equals(action)) {
                cart.addItem(req.getParameter("item"));
                message = "Item added.";
            } else if ("remove".equals(action)) {
                cart.removeItem(req.getParameter("item"));
                message = "Item removed.";
            } else if ("checkout".equals(action)) {
                message = "Checked out " + cart.getItemCount() + " item(s) for "
                        + cart.getCustomerName() + ".";
                cart.remove(); // ends the conversation -> container calls ejbRemove()
                session.removeAttribute(CART_ATTR);
            }
        } catch (CreateException e) {
            message = "Could not create cart: " + e.getMessage();
        } catch (ItemNotFoundException e) {
            message = e.getMessage();
        } catch (NoSuchObjectLocalException e) {
            // Bean was removed or timed out on the server side.
            session.removeAttribute(CART_ATTR);
            message = "Your cart has expired, please start again.";
        } catch (RemoveException e) {
            throw new ServletException(e);
        }
        render(req, resp, message);
    }

    private ShoppingCartLocalHome lookupHome() throws ServletException {
        try {
            return (ShoppingCartLocalHome) new InitialContext().lookup(HOME_JNDI);
        } catch (NamingException e) {
            throw new ServletException("Cannot look up " + HOME_JNDI, e);
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String message)
            throws IOException {
        ShoppingCartLocal cart = (ShoppingCartLocal) req.getSession().getAttribute(CART_ATTR);
        String self = req.getContextPath() + "/cart";

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>EJB 2.x Stateful Cart</title></head><body>");
        out.println("<h1>EJB 2.x Stateful Session Bean Demo</h1>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }

        if (cart == null) {
            out.println("<form method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='login'/>"
                    + "Customer name: <input name='customer'/> <button>Start cart</button></form>");
        } else {
            out.println("<p>Cart for <b>" + escape(cart.getCustomerName()) + "</b> ("
                    + cart.getItemCount() + " item(s))</p><ul>");
            for (String item : cart.getItems()) {
                out.println("<li>" + escape(item)
                        + " <form style='display:inline' method='post' action='" + self + "'>"
                        + "<input type='hidden' name='action' value='remove'/>"
                        + "<input type='hidden' name='item' value='" + escape(item) + "'/>"
                        + "<button>remove</button></form></li>");
            }
            out.println("</ul>");
            out.println("<form method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='add'/>"
                    + "Item: <input name='item'/> <button>Add</button></form>");
            out.println("<form method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='checkout'/>"
                    + "<button>Checkout (removes the bean)</button></form>");
        }
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
