package org.urajio.freshpastry.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.routing.SendOptions;

/**
 * A hello world example for pastry. This is the per-node app object.
 *
 * @author Sitaram Iyer
 * @version $Id$
 */
public class HelloWorldApp extends PastryAppl {
    private final static Logger logger = LoggerFactory.getLogger(HelloWorldApp.class);
    private static int addr = HelloAddress.getCode();
    private int msgid = 0;

    public HelloWorldApp(PastryNode pn) {
        super(pn, null, addr, null);
        register();
    }

    /**
     * Sends a message to a randomly chosen node. Yeah, for fun.
     *
     * @param rng Randon number generator.
     */
    public void sendRndMsg(RandomSource rng) {
        Id randomId = Id.makeRandomId(rng);
        logger.debug("Sending message from " + getNodeId() + " to random dest " + randomId);

        Message msg = new HelloMsg(addr, thePastryNode.getLocalHandle(), randomId, ++msgid);
        routeMsg(randomId, msg, new SendOptions());
    }

    /**
     * Get address.
     *
     * @return the address of this application.
     */
    public int getAddress() {
        return addr;
    }

    /**
     * Invoked on destination node when a message arrives.
     *
     * @param msg Message being routed around
     */
    public void messageForAppl(Message msg) {
        logger.debug("Received " + msg + " at " + getNodeId());
    }

    /**
     * Invoked on intermediate nodes in routing path.
     *
     * @param msg     Message that's passing through this node.
     * @param key     destination
     * @param nextHop next hop
     * @param opt     send options
     * @return true if message needs to be forwarded according to plan.
     */
    public boolean enrouteMessage(Message msg, Id key, NodeHandle nextHop, SendOptions opt) {
        logger.debug("Enroute " + msg + " at " + getNodeId());

        return true;
    }

    /**
     * Invoked upon change to leafset.
     *
     * @param nh       node handle that got added/removed
     * @param wasAdded added (true) or removed (false)
     */
    public void leafSetChange(NodeHandle nh, boolean wasAdded) {
        if (logger.isDebugEnabled()) {
            String s = "In " + getNodeId() + "'s leaf set, " + "node " + nh.getNodeId() + " was ";
            if (wasAdded) {
                s += "added";
            } else {
                s += "removed";
            }

            System.out.println(s);
        }
    }

    /**
     * Invoked upon change to routing table.
     *
     * @param nh       node handle that got added/removed
     * @param wasAdded added (true) or removed (false)
     */
    public void routeSetChange(NodeHandle nh, boolean wasAdded) {
        if (logger.isDebugEnabled()) {
            String s = "In " + getNodeId() + "'s route set, " + "node " + nh.getNodeId() + " was ";
            if (wasAdded) {
                s += "added";
            } else {
                s += "removed";
            }
            System.out.println(s);
        }
    }

    /**
     * Invoked by {RMI,Direct}PastryNode when the node has something in its leaf
     * set, and has become ready to receive application messages.
     */
    public void notifyReady() {
        logger.info("Node " + getNodeId() + " ready, waking up any clients");
    }

    private static class HelloAddress {
        private static int myCode = 0x1984abcd;

        public static int getCode() {
            return myCode;
        }
    }
}

