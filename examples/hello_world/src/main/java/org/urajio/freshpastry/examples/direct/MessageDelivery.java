package org.urajio.freshpastry.examples.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.examples.transport.direct.Delivery;
import org.urajio.freshpastry.examples.transport.direct.DirectTransportLayer;

import java.io.IOException;
import java.util.Map;


/**
 * @author amislove
 * @version $Id: EuclideanNetwork.java 2561 2005-06-09 16:22:02Z jeffh $
 */
class MessageDelivery<Identifier, MessageType> implements Delivery {
    private final static Logger logger = LoggerFactory.getLogger(MessageDelivery.class);

    protected MessageType msg;
    protected Identifier node;
    protected int seq;
    protected Map<String, Object> options;
    BasicNetworkSimulator<Identifier, MessageType> networkSimulator;
    Identifier from;

    /**
     * Constructor for MessageDelivery.
     */
    public MessageDelivery(MessageType m, Identifier to, Identifier from, Map<String, Object> options, BasicNetworkSimulator<Identifier, MessageType> sim) {
        msg = m;
        node = to;
        this.options = options;
        this.from = from;
        this.networkSimulator = sim;
        this.seq = sim.getTL(to).getNextSeq();
    }

    public void deliver() {
        logger.debug("MD: deliver " + msg + " to " + node);
        try {
            DirectTransportLayer<Identifier, MessageType> tl = networkSimulator.getTL(node);
            if (tl != null) {
                networkSimulator.notifySimulatorListenersReceived(msg, from, node);
                tl.incomingMessage(from, msg, options);
            } else {
                logger.warn("Message " + msg + " dropped because destination " + node + " is dead.");
                // Notify sender? tough decision, this would be lost in the network, but over tcp, this would be noticed
                // maybe notify sender if tcp?
            }
        } catch (IOException ioe) {
            logger.warn("Error delivering message " + this, ioe);
        }
    }

    public int getSeq() {
        return seq;
    }

    public String toString() {
        return "MD[" + msg + ":" + from + "=>" + node + ":" + seq + "]";
    }
}