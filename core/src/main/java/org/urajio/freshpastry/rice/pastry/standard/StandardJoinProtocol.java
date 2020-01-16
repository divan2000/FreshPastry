package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.join.InitiateJoin;
import org.urajio.freshpastry.rice.pastry.join.JoinAddress;
import org.urajio.freshpastry.rice.pastry.join.JoinProtocol;
import org.urajio.freshpastry.rice.pastry.join.JoinRequest;
import org.urajio.freshpastry.rice.pastry.leafset.BroadcastLeafSet;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.messaging.PJavaSerializedDeserializer;
import org.urajio.freshpastry.rice.pastry.routing.*;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * An implementation of a simple join protocol.
 * <p>
 * Overview:
 * initiateJoin() causes InitiateJoinMsg to be sent periodically on the joiner
 * causes handleInitiateJoin() constructs a RouteMessage(JoinRequest) and sends it to the boostrap
 * causes handleIntermediateHop() on each node along the route
 * causes respondToJoiner() on last hop
 * causes completeJoin() on joiner
 *
 * @author Peter Druschel
 * @author Andrew Ladd
 * @author Rongmei Zhang
 * @author Y. Charlie Hu
 * @author Jeff Hoye
 * @version $Id$
 */

public class StandardJoinProtocol extends PastryAppl implements JoinProtocol {
    private final static Logger logger = LoggerFactory.getLogger(StandardJoinProtocol.class);
    protected final LeafSet leafSet;
    protected NodeHandle localHandle;
    protected RoutingTable routeTable;
    // join retransmission stuff
    protected ScheduledMessage joinEvent;

    public StandardJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt, LeafSet ls) {
        this(ln, lh, rt, ls, null);
    }

    public StandardJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt, LeafSet ls, MessageDeserializer md) {
        super(ln, null, JoinAddress.getCode(), md == null ? new SJPDeserializer(ln) : md);
        localHandle = lh;

        routeTable = rt;
        leafSet = ls;
    }

    /**
     * Get address.
     *
     * @return gets the address.
     */
    public int getAddress() {
        return JoinAddress.getCode();
    }

    public void initiateJoin(Collection<NodeHandle> bootstrap) {
        logger.debug("initiateJoin(" + bootstrap + ")");
        if (bootstrap == null || bootstrap.isEmpty()) {
            // no bootstrap node, so ready immediately
            thePastryNode.setReady();
        } else {
            // schedule (re-)transmission of the join message at an exponential backoff
            joinEvent = new ExponentialBackoffScheduledMessage(
                    thePastryNode, new InitiateJoin(bootstrap),
                    thePastryNode.getEnvironment().getSelectorManager().getTimer(),
                    0, 2000, 2, 60000);
        }
    }

    /**
     * Receives a message from the outside world.
     *
     * @param msg the message that was received.
     */
    public void receiveMessage(Message msg) {
        if (msg instanceof JoinRequest) {
            JoinRequest jr = (JoinRequest) msg;
            handleJoinRequest(jr);
        } else if (msg instanceof RouteMessage) { // a join request message at an intermediate node
            RouteMessage rm = (RouteMessage) msg;
            handleIntermediateHop(rm);
        } else if (msg instanceof InitiateJoin) { // request from the local node to join
            InitiateJoin ij = (InitiateJoin) msg;
            handleInitiateJoin(ij);
        }
    }

    protected void handleInitiateJoin(InitiateJoin ij) {
        final NodeHandle nh = ij.getHandle();

        if (nh == null) {
            logger.error("ERROR: Cannot join ring.  All bootstraps are faulty." + ij);
            thePastryNode.joinFailed(new JoinFailedException("Cannot join ring.  All bootstraps are faulty." + ij));
        } else {
            logger.info("InitiateJoin attempting to join:" + nh + " liveness:" + nh.getLiveness());
            getJoinRequest(nh, new Continuation<JoinRequest, Exception>() {

                public void receiveResult(JoinRequest jr) {
                    RouteMessage rm = new RouteMessage(localHandle.getNodeId(), jr, null, null,
                            (byte) thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));

                    rm.getOptions().setRerouteIfSuspected(false);
                    rm.setPrevNode(localHandle);
                    thePastryNode.send(nh, rm, null, getOptions(jr, options));
                }

                public void receiveException(Exception exception) {
                }
            });
        }
    }

    protected void getJoinRequest(NodeHandle bootstrap, Continuation<JoinRequest, Exception> deliverJRToMe) {
        JoinRequest jr = new JoinRequest(localHandle, thePastryNode
                .getRoutingTable().baseBitLength(), thePastryNode.getEnvironment().getTimeSource().currentTimeMillis());
        deliverJRToMe.receiveResult(jr);
    }

    protected void handleIntermediateHop(RouteMessage rm) {
        try {
            JoinRequest jr = (JoinRequest) rm.unwrap(deserializer);

            Id localId = localHandle.getNodeId();
            NodeHandle jh = jr.getHandle();
            Id nid = jh.getNodeId();

            if (!jh.equals(localHandle)) {
                int base = thePastryNode.getRoutingTable().baseBitLength();

                int msdd = localId.indexOfMSDD(nid, base);
                int last = jr.lastRow();

                for (int i = last - 1; msdd > 0 && i >= msdd; i--) {
                    RouteSet[] row = routeTable.getRow(i);

                    jr.pushRow(row);
                }

                rm.setRouteMessageNotification(new RouteMessageNotification() {
                    public void sendSuccess(RouteMessage message, NodeHandle nextHop) {
                        logger.debug("sendSuccess(" + message + "):" + nextHop);
                    }

                    public void sendFailed(RouteMessage message, Exception e) {
                        logger.debug("sendFailed(" + message + ") ", e);
                    }
                });

                rm.setTLOptions(getOptions(jr, rm.getTLOptions()));

                logger.debug("Routing " + rm);
                thePastryNode.getRouter().route(rm);
            }
        } catch (IOException ioe) {
            logger.error("StandardJoinProtocol.receiveMessage()", ioe);
        }
    }

    protected void handleJoinRequest(final JoinRequest jr) {
        if (jr.accepted() == false) {
            respondToJoiner(jr);
        } else { // this is the node that initiated the join request in the first
            // place
            completeJoin(jr);
        }
    }

    protected void respondToJoiner(final JoinRequest jr) {
        NodeHandle joiner = jr.getHandle();

        // this is the terminal node on the request path
        // leafSet.put(joiner);
        if (thePastryNode.isReady()) {
            logger.debug("acceptJoin " + jr);
            jr.acceptJoin(localHandle, leafSet);
            thePastryNode.send(joiner, jr, new PMessageNotification() {
                public void sent(PMessageReceipt msg) {
                    logger.debug("acceptJoin.sent(" + msg + "):" + jr);
                }

                public void sendFailed(PMessageReceipt msg, Exception reason2) {
                    Throwable reason = reason2;

                    logger.debug("acceptJoin.sendFailed(" + msg + "):" + jr, reason);

                }
            }, getOptions(jr, options));
        } else {
            logger.info("NOTE: Dropping incoming JoinRequest " + jr + " because local node is not ready!");
        }
    }

    protected Map<String, Object> getOptions(JoinRequest jr, Map<String, Object> existing) {
        return existing;
    }

    /**
     * called on the joiner
     */
    protected void completeJoin(JoinRequest jr) {
        NodeHandle jh = jr.getJoinHandle(); // the node we joined to.

        if (jh.getId().equals(localHandle.getId()) && !jh.equals(localHandle)) {
            logger.warn("NodeId collision, unable to join: " + localHandle + ":" + jh);
        } else if (jh.isAlive() == true) { // the join handle is alive
            routeTable.put(jh);
            // add the num. closest node to the routing table

            // now update the local leaf set
            BroadcastLeafSet bls = new BroadcastLeafSet(jh, jr.getLeafSet(),
                    BroadcastLeafSet.JoinInitial, 0);
            thePastryNode.receiveMessage(bls);

            // update local RT, then broadcast rows to our peers
            broadcastRows(jr);

            // we have now successfully joined the ring, set the local node ready
            setReady();
        }
    }

    /**
     * Can be overloaded to do additional things before going ready. For example,
     * verifying that other nodes are aware of us, so that consistent routing is
     * guaranteed.
     */
    protected void setReady() {
        if (joinEvent != null) joinEvent.cancel();
        joinEvent = null;

        thePastryNode.setReady();
    }

    /**
     * Broadcasts the route table rows.
     *
     * @param jr the join row.
     */

    public void broadcastRows(JoinRequest jr) {
        int n = jr.numRows();

        // send the rows to the RouteSetProtocol on the local node
        for (int i = jr.lastRow(); i < n; i++) {
            RouteSet[] row = jr.getRow(i);

            if (row != null) {
                BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

                thePastryNode.receiveMessage(brr);
            }
        }

        // now broadcast the rows to our peers in each row

        for (int i = jr.lastRow(); i < n; i++) {
            RouteSet[] row = jr.getRow(i);

            BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

            for (RouteSet rs : row) {
                if (rs == null)
                    continue;

                // send to closest nodes only

                NodeHandle nh = rs.closestNode();
                if (nh != null)
                    thePastryNode.send(nh, brr, null, options);
            }
        }
    }

    /**
     * Should not be called becasue we are overriding the receiveMessage()
     * interface anyway.
     */
    public void messageForAppl(Message msg) {
        throw new RuntimeException("Should not be called.");
    }

    /**
     * We always want to receive messages.
     */
    public boolean deliverWhenNotReady() {
        return true;
    }

    public static class SJPDeserializer extends PJavaSerializedDeserializer {
        public SJPDeserializer(PastryNode pn) {
            super(pn);
        }

        @Override
        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            if (type == JoinRequest.TYPE) {
                return new JoinRequest(buf, pn, (NodeHandle) sender, pn);
            }
            return null;
        }
    }
}
