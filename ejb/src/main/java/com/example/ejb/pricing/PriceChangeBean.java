package com.example.ejb.pricing;

import com.example.ejb.product.ProductLocal;
import com.example.ejb.product.ProductLocalHome;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.ObjectNotFoundException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * EJB 2.x message-driven bean.
 * <p>
 * It implements {@link MessageDrivenBean} for the container callbacks and
 * {@link MessageListener} for delivery, and is declared in ejb-jar.xml with no
 * annotations. Unlike session and entity beans, an MDB has no home or
 * component interface: clients can't call it, they only send messages to the
 * queue it listens on.
 * <p>
 * Each {@code MapMessage} ({@code sku}, {@code percent}) discounts one product.
 * {@link #onMessage} runs in a container-managed transaction (Required), so
 * receiving the message and updating the entity commit or roll back together.
 * If it rolls back, the JMS provider redelivers the message.
 */
public class PriceChangeBean implements MessageDrivenBean, MessageListener {

    private static final long serialVersionUID = 1L;
    private static final String PRODUCT_HOME_JNDI = "java:comp/env/ejb/Product";

    private MessageDrivenContext messageDrivenContext;
    private ProductLocalHome productHome;

    @Override
    public void setMessageDrivenContext(MessageDrivenContext ctx) {
        this.messageDrivenContext = ctx;
    }

    /** Called once when the container adds an instance to the MDB pool. */
    public void ejbCreate() {
        try {
            productHome = (ProductLocalHome) new InitialContext().lookup(PRODUCT_HOME_JNDI);
        } catch (NamingException e) {
            throw new EJBException("Cannot look up " + PRODUCT_HOME_JNDI, e);
        }
        log("ejbCreate");
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (!(message instanceof MapMessage)) {
                // A message that can never be processed: log it and let it commit.
                // Rolling back would only make the provider redeliver it forever.
                log("Ignoring unsupported message type " + message.getClass().getName());
                return;
            }
            MapMessage map = (MapMessage) message;
            String sku = map.getString("sku");
            int percent = map.getInt("percent");
            log("Received " + message.getJMSMessageID() + " sku=" + sku + " percent=" + percent
                    + (message.getJMSRedelivered() ? " (redelivered)" : ""));

            if (percent <= 0 || percent >= 100) {
                log("Ignoring invalid discount " + percent + "% for " + sku);
                return;
            }
            ProductLocal product = productHome.findByPrimaryKey(sku);
            product.applyDiscount(percent);
            log("New price for " + sku + ": " + product.getPrice());
        } catch (ObjectNotFoundException e) {
            log("No product with SKU in message, discarding: " + e.getMessage());
        } catch (FinderException e) {
            // Possibly temporary (e.g. a database problem): roll back so the
            // message is redelivered.
            log("Finder failed, rolling back for redelivery: " + e);
            messageDrivenContext.setRollbackOnly();
        } catch (JMSException e) {
            log("Bad message, rolling back: " + e);
            messageDrivenContext.setRollbackOnly();
        }
    }

    @Override
    public void ejbRemove() {
        log("ejbRemove");
    }

    private void log(String event) {
        System.out.println("[PriceChangeBean@" + Integer.toHexString(System.identityHashCode(this)) + "] " + event);
    }
}
