package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.rice.p2p.commonapi.exception.AppNotRegisteredException;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.messaging.MessageDispatch;
import org.urajio.freshpastry.rice.pastry.routing.*;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.util.*;

/**
 * An implementation of the standard Pastry routing algorithm.
 *
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y.Charlie Hu
 * @version $Id$
 */

public class StandardRouter extends PastryAppl implements Router {
    private final static Logger logger = LoggerFactory.getLogger(StandardRouter.class);
    /**
     * We can end up causing a nasty feedback if we blast too many BRRs, so we're
     * going to throttle.
     */
    protected final Map<NodeHandle, Long> lastTimeSentRouteTablePatch = new HashMap<>();
    protected RouterStrategy routerStrategy;
    protected int ROUTE_TABLE_PATCH_THROTTLE = 5000;
    MessageDispatch dispatch;

    /**
     * Constructor.
     */
    public StandardRouter(final PastryNode thePastryNode, MessageDispatch dispatch) {
        this(thePastryNode, dispatch, new AliveRouterStrategy());
    }

    public StandardRouter(final PastryNode thePastryNode, MessageDispatch dispatch, RouterStrategy strategy) {
        super(thePastryNode, null, RouterAddress.getCode(), new MessageDeserializer() {

            public org.urajio.freshpastry.rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority,
                                                                                 org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle sender) throws IOException {
                RouteMessage rm;
                rm = RouteMessage.build(buf, (byte) priority, thePastryNode, (NodeHandle) sender,
                        (byte) thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));
                return rm;
            }
        });
        this.dispatch = dispatch;
        this.routerStrategy = strategy;
        if (routerStrategy == null) {
            routerStrategy = new AliveRouterStrategy();
        }
    }

    public void setRouterStrategy(RouterStrategy strategy) {
        this.routerStrategy = strategy;
    }

    /**
     * Receive a message from a remote node.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
        logger.debug("receiveMessage(" + msg + ")");
        if (msg instanceof RouteMessage) {
            // should only happen for messages coming off the wire
            route((RouteMessage) msg);
        } else {
            throw new Error("message " + msg + " bounced at StandardRouter");
        }
    }

    public void route(final RouteMessage rm) {
        if (!thePastryNode.getEnvironment().getSelectorManager().isSelectorThread()) {
            thePastryNode.getEnvironment().getSelectorManager().invoke(new Runnable() {
                public void run() {
                    route(rm);
                }
            });
            return;
        }
        logger.debug("route(" + rm + ")");
        if (routeMessage(rm) == false)
            receiveRouteMessage(rm);
    }

    /**
     * Routes the messages if the next hop has been set up.
     * <p>
     * Note, this once lived in the RouteMessaage
     */
    public boolean routeMessage(RouteMessage rm) {
        logger.debug("routeMessage(" + rm + ")");
        if (rm.getNextHop() == null)
            return false;
        rm.setSender(thePastryNode.getLocalHandle());

        NodeHandle handle = rm.getNextHop();
        rm.setNextHop(null);
        rm.setPrevNode(thePastryNode.getLocalHandle());

        if (thePastryNode.getLocalHandle().equals(handle)) {
            // the local node is the closest node to the id
            if (rm.getDestinationHandle() != null && !rm.getDestinationHandle().equals(thePastryNode.getLocalHandle())) {
                // no idea how to contact the destination, drop
                logger.debug("Message " + rm + " has destination " + rm.getDestinationHandle() + " but I'm the root of the id.  Dropping.  This could happen if the destination has died while the route message was in transit, or if the local node does not yet have logging state because it is boostrapping.");

                rm.sendFailed(new NoRouteToHostException(rm.getDestinationHandle().toString()));
                return true;
            }
            thePastryNode.receiveMessage(rm.internalMsg);
            rm.sendSuccess(thePastryNode.getLocalHandle());
        } else {
            sendTheMessage(rm, handle);
        }
        return true;
    }

    protected void sendTheMessage(final RouteMessage rm, final NodeHandle handle) {
        logger.debug("sendTheMessage(" + rm + "," + handle + ")");
        rm.setTLCancellable(thePastryNode.send(handle, rm, new PMessageNotification() {
            @Override
            public void sent(PMessageReceipt msg) {
                rm.sendSuccess(handle);
            }

            @Override
            public void sendFailed(PMessageReceipt msg, Exception reason) {
                // TODO: wtf???
                if (rm.sendFailed(reason)) {
                    logger.debug("sendFailed(" + rm + ")=>" + handle, reason);
                } else {
                    logger.debug("sendFailed(" + rm + ")=>" + handle, reason);
                    if (reason instanceof NodeIsFaultyException) {
                        logger.info("sendFailed(" + rm + ")=>" + handle + " " + reason);
                    } else {
                        logger.warn("sendFailed(" + rm + ")=>" + handle, reason);
                    }
                }
            }
        }, rm.getTLOptions()));
    }

    /**
     * Receive and process a route message.  Sets a nextHop.
     *
     * @param msg the message.
     */
    private void receiveRouteMessage(RouteMessage msg) {
        logger.debug("receiveRouteMessage(" + msg + ")");
        Id target = msg.getTarget();

        if (target == null)
            target = thePastryNode.getNodeId();

        int cwSize = thePastryNode.getLeafSet().cwSize();
        int ccwSize = thePastryNode.getLeafSet().ccwSize();

        int lsPos = thePastryNode.getLeafSet().mostSimilar(target);

        if (lsPos == 0) {
            // message is for the local node so deliver it
            msg.setNextHop(thePastryNode.getLocalHandle());

            // don't return, we want to check for routing table hole
        } else {
            msg.getOptions().setRerouteIfSuspected(true);
            Iterator<NodeHandle> i = getBestRoutingCandidates(target);

            // the next hop
            NodeHandle nextHop = routerStrategy.pickNextHop(msg, i);
            if (nextHop == null) {
                msg.sendFailed(new NoLegalRouteToMakeProgressException(target));
                return;
            }
            msg.setNextHop(nextHop);
        }

        // this wasn't being called often enough in its previous location, moved here Aug 11, 2006
        checkForRouteTableHole(msg, msg.getNextHop());
        msg.setPrevNode(thePastryNode.getLocalHandle());

        // here, we need to deliver the msg to the proper app
        deliverToApplication(msg);
    }

    public Iterator<NodeHandle> getBestRoutingCandidates(final Id target) {
        int cwSize = thePastryNode.getLeafSet().cwSize();
        int ccwSize = thePastryNode.getLeafSet().ccwSize();

        int lsPos = thePastryNode.getLeafSet().mostSimilar(target);

        if (lsPos == 0) {
            // message is for the local node so deliver it
            return Collections.singleton(thePastryNode.getLocalHandle()).iterator();
        }

        boolean leafSetOnly = false;
        if ((lsPos > 0 && (lsPos < cwSize || !thePastryNode.getLeafSet().get(lsPos).getNodeId().clockwise(target)))
                || (lsPos < 0 && (-lsPos < ccwSize || thePastryNode.getLeafSet().get(lsPos).getNodeId().clockwise(target)))) {
            leafSetOnly = true;
        }
        return getBestRoutingCandidates(target, lsPos, leafSetOnly);
    }

    protected Iterator<NodeHandle> getBestRoutingCandidates(final Id target, final int lsPos, boolean leafSetOnly) {
        // these are the leafset entries between me and the closest known node
        if (leafSetOnly) {
            return getLSCollection(lsPos).iterator();
        }

        // try the routing table first
        return new Iterator<NodeHandle>() {
            Iterator<NodeHandle> rtIterator = null;
            Iterator<NodeHandle> iterator = null;
            ArrayList<NodeHandle> lsCollection = null;
            NodeHandle next;
            RouteSet best;
            int k = 0; // used to iterate over best

            // set the first candidates, if there is no best, then go with the rest
            {
                best = thePastryNode.getRoutingTable().getBestEntry(target);
                if (best == null || best.isEmpty()) {
                    rtIterator = thePastryNode.getRoutingTable().alternateRoutesIterator(target);
                    lsCollection = getLSCollection(lsPos);
                    iterator = rtIterator;
                }
                next = getNext();
            }


            public boolean hasNext() {
                if (next == null) next = getNext();
                return (next != null);
            }

            public NodeHandle getNext() {
                // return best candidate first
                if (iterator == null && best != null) {
                    NodeHandle ret = best.get(k);
                    k++;
                    if (k >= best.size()) {
                        // done with best, now use the rtIterator
                        rtIterator = thePastryNode.getRoutingTable().alternateRoutesIterator(target);
                        lsCollection = getLSCollection(lsPos);
                        iterator = rtIterator;
                    }
                    return ret;
                }

                // try the routing table
                if (iterator.hasNext()) {
                    NodeHandle ret = iterator.next();

                    // don't return nodes from best
                    if (best != null && best.getIndex(ret) != -1) {
                        return getNext();
                    }

                    // if this goes into the leafset, then stop using the rtIterator
                    if (iterator == rtIterator && lsCollection.contains(ret)) {
                        iterator = lsCollection.iterator();
                        return iterator.next(); // this will always succeed, since it contains the old version of next, even though it's unguarded by hasNext()
                    } else {
                        return ret;
                    }
                } else {
                    // switch to the leafset iterator if possible
                    if (iterator == rtIterator) {
                        iterator = lsCollection.iterator();
                        return getNext();
                    }
                }
                return null;
            }

            public NodeHandle next() {
                if (hasNext()) {
                    NodeHandle ret = next;
                    next = null;
                    return ret;
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                throw new RuntimeException("Operation not allowed.");
            }
        };
    }

    protected ArrayList<NodeHandle> getLSCollection(int lsPos) {
        ArrayList<NodeHandle> lsCollection = new ArrayList<>();
        if (lsPos > 0) {
            // search for someone between us who is alive
            for (int i = lsPos; i > 0; i--) {
                NodeHandle temp = thePastryNode.getLeafSet().get(i);
                lsCollection.add(temp);
            }
        } else { // lsPos < 0
            for (int i = lsPos; i < 0; i++) {
                NodeHandle temp = thePastryNode.getLeafSet().get(i);
                lsCollection.add(temp);
            }
        }
        return lsCollection;
    }

    public void deliverToApplication(RouteMessage msg) {
        PastryAppl appl = dispatch.getDestinationByAddress(msg.getAuxAddress());
        if (appl == null) {
            if (msg.sendFailed(new AppNotRegisteredException(msg.getAuxAddress()))) {
                logger.debug(
                        "Dropping message " + msg + " because the application address " + msg.getAuxAddress() + " is unknown.");
            } else {
                logger.warn(
                        "Dropping message " + msg + " because the application address " + msg.getAuxAddress() + " is unknown.");
            }
            return;
        }
        appl.receiveMessage(msg);
//    thePastryNode.receiveMessage(msg);
    }

    /**
     * checks to see if the previous node along the path was missing a RT entry if
     * so, we send the previous node the corresponding RT row to patch the hole
     *
     * @param msg    the RouteMessage being routed
     * @param handle the next hop handle
     */

    private void checkForRouteTableHole(RouteMessage msg, NodeHandle handle) {
        logger.debug("checkForRouteTableHole(" + msg + "," + handle + ")");

        NodeHandle prevNode = msg.getPrevNode();
        if (prevNode == null) {
            logger.debug("No prevNode defined in " + msg);
            return;
        }

        if (prevNode.equals(getNodeHandle())) {
            logger.debug("prevNode is me in " + msg);
            return;
        }

        // we don't want to send the repair if they just routed in the leafset
        LeafSet ls = thePastryNode.getLeafSet();
        if (ls.overlaps()) return; // small network, don't bother
        if (ls.member(prevNode)) {
            // ok, it's in my leafset, so I'm in his, but make sure that it's not on the edge
            int index = ls.getIndex(prevNode);
            if ((index == ls.cwSize()) || (index == -ls.ccwSize())) {
                // it is the edge... continue with repair
            } else {
                return;
            }
        }

        Id prevId = prevNode.getNodeId();
        Id key = msg.getTarget();

        int diffDigit = prevId.indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength());

        // if we both have the same prefix (in other words the previous node didn't make a prefix of progress)
        if (diffDigit >= 0 &&
                diffDigit == thePastryNode.getNodeId().indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength())) {
            synchronized (lastTimeSentRouteTablePatch) {
                if (lastTimeSentRouteTablePatch.containsKey(prevNode)) {
                    long lastTime = lastTimeSentRouteTablePatch.get(prevNode);
                    if (lastTime > (thePastryNode.getEnvironment().getTimeSource().currentTimeMillis() - ROUTE_TABLE_PATCH_THROTTLE)) {
                        logger.info("not sending route table patch to " + prevNode + " because throttled.  Last Time:" + lastTime);
                        return;
                    }
                }
                lastTimeSentRouteTablePatch.put(prevNode, thePastryNode.getEnvironment().getTimeSource().currentTimeMillis());
            }

            // the previous node is missing a RT entry, send the row
            // for now, we send the entire row for simplicity

            RouteSet[] row = thePastryNode.getRoutingTable().getRow(diffDigit);
            BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);

            if (prevNode.isAlive()) {
                logger.debug("Found hole in " + prevNode + "'s routing table. Sending " + brr.toStringFull());

                thePastryNode.send(prevNode, brr, null, options);
            }
        }
    }

    public boolean deliverWhenNotReady() {
        return true;
    }

    public void messageForAppl(Message msg) {
        throw new RuntimeException("Should not be called.");
    }

    /**
     * Try to return someone who isn't suspected.  If they're all suspected,
     * choose the first candidate, but set the rerouteIfSuspected option to false.
     *
     * @author Jeff Hoye
     */
    static class AliveRouterStrategy implements RouterStrategy {
        public NodeHandle pickNextHop(RouteMessage msg, Iterator<NodeHandle> i) {
            NodeHandle first = i.next();
            if (first.getLiveness() < NodeHandle.LIVENESS_SUSPECTED) {
                return first;
            }
            while (i.hasNext()) {
                NodeHandle nh = i.next();
                if (nh.getLiveness() < NodeHandle.LIVENESS_SUSPECTED) {
                    return nh;
                }
                // do this in case first is dead, and we find a suspected node to pass it to
                if (first.getLiveness() > nh.getLiveness()) first = nh;
            }
            if (first.getLiveness() >= NodeHandle.LIVENESS_DEAD) {
                // drop the message, this would happen if we gave a lease to this node
                // but found him faulty.
                return null;
            }
            msg.getOptions().setRerouteIfSuspected(false);
            return first; // always want to return someone...
        }
    }
}

