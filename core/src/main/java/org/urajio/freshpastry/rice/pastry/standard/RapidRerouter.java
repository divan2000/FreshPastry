package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.QueueOverflowException;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.messaging.MessageDispatch;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;
import org.urajio.freshpastry.rice.pastry.routing.RouterStrategy;
import org.urajio.freshpastry.rice.pastry.routing.SendOptions;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * The superclass makes the routing decisions.  This class handles the rapid-rerouting.
 *
 * @author Jeff Hoye
 */
public class RapidRerouter extends StandardRouter implements LivenessListener<NodeHandle> {
    /**
     * The max times to try to reroute a message.
     */
    public static final int MAX_RETRIES = 10;
    private final static Logger logger = LoggerFactory.getLogger(RapidRerouter.class);
    /**
     * These messages should be rapidly rerouted if the node goes suspected.
     */
    final Map<NodeHandle, Collection<RouterNotification>> pending;

    public RapidRerouter(PastryNode thePastryNode, MessageDispatch dispatch, RouterStrategy strategy) {
        super(thePastryNode, dispatch, strategy);
        pending = new HashMap<>();

        thePastryNode.addLivenessListener(this);
    }

    @Override
    protected void sendTheMessage(RouteMessage rm, NodeHandle handle) {
//    logger.log("sendTheMessage("+rm+","+handle+") reroute:"+rm.getOptions().rerouteIfSuspected());
        if (rm.getOptions().multipleHopsAllowed() && rm.getOptions().rerouteIfSuspected()) {
            // can be rapidly rerouted
            if (handle.getLiveness() >= LIVENESS_SUSPECTED) {
//        if (logger.level <= Logger.WARNING) logger.log("Reroutable message "+rm+" sending to non-alive node:"+handle+" liveness:"+handle.getLiveness());
                super.sendTheMessage(rm, handle);
                return;
            }

            RouterNotification notifyMe = new RouterNotification(rm, handle);
            addToPending(notifyMe, handle);
            rm.setTLCancellable(notifyMe);
            notifyMe.setCancellable(thePastryNode.send(handle, rm, notifyMe, rm.getTLOptions()));
        } else {
            super.sendTheMessage(rm, handle);
        }
    }

    protected void rerouteMe(final RouteMessage rm, NodeHandle oldDest, Exception ioe) {
        logger.debug("rerouteMe(" + rm + " oldDest:" + oldDest + ")");

        rm.numRetries++;
        if (rm.numRetries > MAX_RETRIES) {
            // TODO: Notify some kind of Error Handler
            boolean dontPrint = false;
            if (ioe == null) {
                dontPrint = rm.sendFailed(new TooManyRouteAttempts(rm, MAX_RETRIES));
            } else {
                dontPrint = rm.sendFailed(ioe);
            }
            if (dontPrint) {
                logger.debug("rerouteMe() dropping " + rm + " after " + rm.numRetries + " attempts to (re)route.");
            } else {
                logger.warn("rerouteMe() dropping " + rm + " after " + rm.numRetries + " attempts to (re)route.");
            }
            return;
        }

        // give the selector a chance to do some IO before trying to schedule again
        thePastryNode.getEnvironment().getSelectorManager().invoke(new Runnable() {
            @Override
            public void run() {
                // this is going to make forward() be called again, can prevent this with a check in getPrevNode().equals(localNode)
                rm.getOptions().setRerouteIfSuspected(SendOptions.defaultRerouteIfSuspected);
                route(rm);
            }
        });
    }

    private void addToPending(RouterNotification notifyMe, NodeHandle handle) {
        logger.debug("addToPending(" + notifyMe + " to:" + handle + ")");
        synchronized (pending) {
            Collection<RouterNotification> c = pending.get(handle);
            if (c == null) {
                c = new HashSet<>();
                pending.put(handle, c);
            }
            c.add(notifyMe);
        }
    }

    /**
     * Return true if it was still pending.
     *
     * @param notifyMe
     * @param handle
     * @return true if still pending
     */
    private boolean removeFromPending(RouterNotification notifyMe, NodeHandle handle) {
        synchronized (pending) {
            Collection<RouterNotification> c = pending.get(handle);
            if (c == null) {
                logger.debug("removeFromPending(" + notifyMe + "," + handle + ") had no pending messages for handle.");
                return false;
            }
            boolean ret = c.remove(notifyMe);
            if (c.isEmpty()) {
                pending.remove(handle);
            }
            if (!ret) {
                logger.debug("removeFromPending(" + notifyMe + "," + handle + ") msg was not there.");
            }
            return ret;
        }
    }

    @Override
    public void livenessChanged(NodeHandle i, int val, Map<String, Object> options) {
        if (val >= LIVENESS_SUSPECTED) {
            Collection<RouterNotification> rerouteMe;
            synchronized (pending) {
                rerouteMe = pending.remove(i);
            }
            if (rerouteMe != null) {
                logger.debug("removing all messages to:" + i);
                for (RouterNotification rn : rerouteMe) {
                    rn.cancel();
                    rerouteMe(rn.rm, rn.dest, null);
                }
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        thePastryNode.removeLivenessListener(this);
    }

    class RouterNotification implements Cancellable, PMessageNotification {
        RouteMessage rm;
        NodeHandle dest;
        PMessageReceipt cancellable;
        boolean failed = false;
        boolean sent = false;
        boolean cancelled = false;

        public RouterNotification(RouteMessage rm, NodeHandle handle) {
            this.rm = rm;
            this.dest = handle;
            logger.debug("RN.ctor() " + rm + " to:" + dest);
        }

        public void setCancellable(PMessageReceipt receipt) {
            if (receipt == null) logger.warn(this + ".setCancellable(null)", new Exception("Stack Trace"));

            this.cancellable = receipt;
        }

        @Override
        public void sendFailed(PMessageReceipt msg, Exception reason) {
            // what to do..., rapidly reroute?
            failed = true;
            cancellable = null;
            rm.setTLCancellable(null);
            if (reason instanceof QueueOverflowException) {
                // todo, reroute this to a node w/o a full queue
                removeFromPending(this, dest);
                if (rm.sendFailed(reason)) {
                    logger.debug("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier(), reason);
                } else {
                    logger.debug("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier(), reason);

                    if (msg.getIdentifier() == null) {
                        logger.info("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier() + " " + reason + " identifier was null!!!", new Exception("Stack Trace"));
                    } else {
                        logger.info("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier() + " " + reason);
                    }
                }

            }

            if (reason instanceof NodeIsFaultyException) {
                if (msg.getIdentifier().isAlive()) {
                    logger.warn("Threw NodeIsFaultyException, and node is alive.  Node:" + msg.getIdentifier() + " Liveness:" + msg.getIdentifier().getLiveness(), reason);
                    logger.warn("RRTrace", new Exception("Stack Trace"));

                }
            }

            if (removeFromPending(this, dest)) {
                logger.info("Send failed on message " + rm + " to " + dest + " rerouting." + msg, reason);
                rerouteMe(rm, dest, reason);
            } else {
                if (rm.sendFailed(reason)) {
                    logger.debug("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier(), reason);
                } else {
                    logger.info("sendFailed(" + msg.getMessage() + ")=>" + msg.getIdentifier(), reason);
                }
            }
        }

        @Override
        public void sent(PMessageReceipt msg) {
            logger.debug("Send success " + rm + " to:" + dest + " " + msg);
            sent = true;
            cancellable = null;
            rm.setTLCancellable(null);
            removeFromPending(this, dest);
            rm.sendSuccess(dest);
        }

        @Override
        public boolean cancel() {
            logger.debug("cancelling " + this);
            if (cancellable == null) logger.warn("cancellable = null c:" + cancelled + " s:" + sent + " f:" + failed);
            cancelled = true;
            if (cancellable != null) return cancellable.cancel();
            return true;
        }

        @Override
        public String toString() {
            return "RN{" + rm + "->" + dest + "}";
        }
    }
}
