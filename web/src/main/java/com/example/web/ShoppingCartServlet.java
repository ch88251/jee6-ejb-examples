package com.example.web;

import static com.example.web.Html.escape;

import com.example.core.cart.CartNotFoundException;
import com.example.core.cart.ItemNotFoundException;
import com.example.core.cart.ShoppingCartService;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Web client for the shopping cart.
 * <p>
 * The HTTP session holds only the cart id. The cart itself lives in Redis
 * ({@link ShoppingCartService}), so any server sharing that Redis can serve the
 * next request, and a remote client given the id would see the same cart.
 * The servlet used to keep a stateful bean's {@code EJBLocalObject} in the session.
 */
public class ShoppingCartServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    static final String CART_ID_ATTR = "cartId";

    private transient ShoppingCartService carts;

    @Override
    public void init() throws ServletException {
        try {
            carts = AppContextListener.service(getServletContext(), ShoppingCartService.class);
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
        String action = req.getParameter("action");
        String message = null;
        HttpSession session = req.getSession();
        String cartId = (String) session.getAttribute(CART_ID_ATTR);

        try {
            if ("login".equals(action)) {
                if (cartId != null) {
                    carts.removeCart(cartId);
                    session.removeAttribute(CART_ID_ATTR);
                }
                cartId = carts.createCart(req.getParameter("customer"));
                session.setAttribute(CART_ID_ATTR, cartId);
                message = "New cart created for " + carts.getCustomerName(cartId);
            } else if (cartId == null) {
                message = "Please start a cart first.";
            } else if ("add".equals(action)) {
                carts.addItem(cartId, req.getParameter("item"));
                message = "Item added.";
            } else if ("remove".equals(action)) {
                carts.removeItem(cartId, req.getParameter("item"));
                message = "Item removed.";
            } else if ("checkout".equals(action)) {
                message = "Checked out " + carts.getItemCount(cartId) + " item(s) for "
                        + carts.getCustomerName(cartId) + ".";
                carts.removeCart(cartId); // ends the conversation: the cart's Redis keys are deleted
                session.removeAttribute(CART_ID_ATTR);
            }
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        } catch (ItemNotFoundException e) {
            message = e.getMessage();
        } catch (CartNotFoundException e) {
            message = expired(session);
        }
        render(req, resp, message);
    }

    /** The cart was checked out elsewhere or timed out in Redis. */
    private static String expired(HttpSession session) {
        session.removeAttribute(CART_ID_ATTR);
        return "Your cart has expired, please start again.";
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        HttpSession session = req.getSession();
        String cartId = (String) session.getAttribute(CART_ID_ATTR);
        String customer = null;
        List<String> items = null;
        if (cartId != null) {
            try {
                customer = carts.getCustomerName(cartId);
                items = carts.getItems(cartId);
            } catch (CartNotFoundException e) {
                message = expired(session);
                customer = null;
            }
        }
        String self = req.getContextPath() + "/cart";

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><head><title>Shopping Cart</title></head><body>");
        out.println("<h1>Shopping Cart (state in Redis)</h1>");
        if (message != null) {
            out.println("<p><strong>" + escape(message) + "</strong></p>");
        }

        if (customer == null) {
            out.println("<form method='post' action='" + self + "'>"
                    + "<input type='hidden' name='action' value='login'/>"
                    + "Customer name: <input name='customer'/> <button>Start cart</button></form>");
        } else {
            out.println("<p>Cart for <b>" + escape(customer) + "</b> (" + items.size() + " item(s))</p><ul>");
            for (String item : items) {
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
                    + "<button>Checkout (deletes the cart)</button></form>");
            out.println("<p>Cart id <code>" + escape(cartId) + "</code>, kept for "
                    + "30 minutes after the last use.</p>");
        }
        out.println("<p><a href='" + req.getContextPath() + "/'>Back</a></p>");
        out.println("</body></html>");
    }
}
