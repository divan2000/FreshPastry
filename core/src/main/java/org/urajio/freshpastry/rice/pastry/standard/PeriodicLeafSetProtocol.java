package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.environment.random.simple.SimpleRandomSource;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.util.TimerWeakHashMap;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.leafset.*;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.messaging.PJavaSerializedDeserializer;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * An implementation of a periodic-style leafset protocol
 *
 * @author Alan Mislove
 * @version $Id$
 */
public class PeriodicLeafSetProtocol extends PastryAppl implements ReadyStrategy, NodeSetListener, Observer, LeafSetProtocol {
    private final static Logger logger = LoggerFactory.getLogger(PeriodicLeafSetProtocol.class);
    /**
     * Related to rapidly determining direct neighbor liveness.
     */
    public final int PING_NEIGHBOR_PERIOD; // 20 sec
    public final int LEASE_PERIOD; // 30 sec
    public final int BLS_THROTTLE; // 10 sec
    protected NodeHandle localHandle;
    protected PastryNode localNode;
    protected LeafSet leafSet;
    protected RoutingTable routeTable;
    /**
     * NodeHandle -> Long remembers the TIME when we received a BLS from that
     * NodeHandle
     */
    protected Map<NodeHandle, Long> lastTimeReceivedBLS; // the leases you have
    protected Map<NodeHandle, Long> lastTimeSentBLS; // the leases you have issued
    ScheduledMessage pingNeighborMessage;

    RandomSource random;

    TimeSource timeSource;
    // Ready Strategy
    boolean hasSetStrategy = false;
    /**
     * Called when the leafset changes
     */
    NodeHandle lastLeft;
    NodeHandle lastRight;
    boolean ready = false;
    /**
     * NodeHandle -> time
     * <p>
     * Leases we have issued.  We cannot remove the node from the leafset until this expires.
     * <p>
     * If this node is found faulty (and you took over the leafset), must go non-ready until lease expires
     */
    Map<NodeHandle, Long> lastTimeRenewedLease;
    boolean destroyed = false;

    /**
     * Builds a periodic leafset protocol
     */
    public PeriodicLeafSetProtocol(PastryNode ln, NodeHandle local,
                                   LeafSet ls, RoutingTable rt) {
        super(ln, null, LeafSetProtocolAddress.getCode(), new PLSPMessageDeserializer(ln));
        this.localNode = ln;
        ln.addObserver(this); // to call start() if we are the seed node

        Parameters params = ln.getEnvironment().getParameters();
        if (params.contains("pastry_periodic_leafset_protocol_use_own_random")
                && params.getBoolean("pastry_periodic_leafset_protocol_use_own_random")) {
            if (params.contains("pastry_periodic_leafset_protocol_random_seed")
                    && !params.getString("pastry_periodic_leafset_protocol_random_seed").equalsIgnoreCase(
                    "clock")) {
                this.random = new SimpleRandomSource(params.getLong("pastry_periodic_leafset_protocol_random_seed"));
            } else {
                this.random = new SimpleRandomSource();
            }
        } else {
            this.random = ln.getEnvironment().getRandomSource();
        }

        this.timeSource = ln.getEnvironment().getTimeSource();
        this.localHandle = local;

        // make sure to register all the existing leafset entries
        this.leafSet = ls;
        for (NodeHandle nh : this.leafSet.asList()) {
            nh.addObserver(this, 50);
        }

        this.routeTable = rt;
        this.lastTimeReceivedBLS = new TimerWeakHashMap<>(ln.getEnvironment().getSelectorManager().getTimer(), 300000);
        this.lastTimeSentBLS = new TimerWeakHashMap<>(ln.getEnvironment().getSelectorManager().getTimer(), 300000);
        Parameters p = ln.getEnvironment().getParameters();
        PING_NEIGHBOR_PERIOD = p.getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period"); // 20 seconds
        LEASE_PERIOD = p.getInt("pastry_protocol_periodicLeafSet_lease_period");  // 30 seconds
        BLS_THROTTLE = p.getInt("pastry_protocol_periodicLeafSet_request_lease_throttle");// 10 seconds
        this.lastTimeRenewedLease = new TimerWeakHashMap<>(ln.getEnvironment().getSelectorManager().getTimer(), LEASE_PERIOD * 2);

        // Removed after meeting on 5/5/2005 Don't know if this is always the
        // appropriate policy.
        // leafSet.addObserver(this);
        pingNeighborMessage = localNode.scheduleMsgAtFixedRate(
                new InitiatePingNeighbor(), PING_NEIGHBOR_PERIOD, PING_NEIGHBOR_PERIOD);
    }

    private void updateRecBLS(NodeHandle from, long time) {
        if (time == 0) return;
        Long oldTime = lastTimeReceivedBLS.get(from);
        if ((oldTime == null) || (oldTime < time)) {
            lastTimeReceivedBLS.put(from, time);
            long leaseTime = time + LEASE_PERIOD - timeSource.currentTimeMillis();
            logger.debug("PLSP.updateRecBLS(" + from + "," + time + "):" + leaseTime);

            if (leaseTime < 10) {
                logger.info("PLSP.updateRecBLS(" + from + "," + time + "):" + leaseTime);
            }

            // need to do this so that nodes are notified
            if (hasSetStrategy)
                isReady();
        }
    }

    /**
     * Receives messages.
     *
     * @param msg the message.
     */
    public void receiveMessage(Message msg) {
        if (msg instanceof BroadcastLeafSet) {
            // receive a leafset from another node
            BroadcastLeafSet bls = (BroadcastLeafSet) msg;

            // if we have now successfully joined the ring, set the local node ready
            if (bls.type() == BroadcastLeafSet.JoinInitial) {
                // merge the received leaf set into our own
                leafSet.merge(bls.leafSet(), bls.from(), routeTable, false,
                        null);

                // localNode.setReady();
                broadcastAll();
            } else {
                // first check for missing entries in their leafset
                NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

                // don't need to remove any nodes that we already found faulty, because leafset.merge does not accept them

                // if we find any missing entries, check their liveness
                for (int i = 0; i < set.size(); i++)
                    if (bls.leafSet().test(set.get(i)))
                        set.get(i).checkLiveness();

                // now check for assumed-dead entries in our leafset
                set = bls.leafSet().neighborSet(Integer.MAX_VALUE);

                // if we find any missing entries, check their liveness
                for (int i = 0; i < set.size(); i++)
                    if (!set.get(i).isAlive())
                        set.get(i).checkLiveness();

                // merge the received leaf set into our own
                leafSet.merge(bls.leafSet(), bls.from(), routeTable, false,
                        null);
            }
            // do this only if you are his proper neighbor !!!
            if ((bls.leafSet().get(1) == localHandle) ||
                    (bls.leafSet().get(-1) == localHandle)) {
                updateRecBLS(bls.from(), bls.getTimeStamp());
            }

        } else if (msg instanceof RequestLeafSet) {
            // request for leaf set from a remote node
            RequestLeafSet rls = (RequestLeafSet) msg;

            if (rls.getTimeStamp() > 0) { // without a timestamp, this is just a normal request, not a lease request
                // remember that we gave out a lease, and go unReady() if the node goes faulty
                lastTimeRenewedLease.put(rls.returnHandle(), timeSource.currentTimeMillis());

                // it's important to put the node in the leafset so that we don't accept messages for a node that
                // we issued a lease to
                // it's also important to record issuing the lease before putting into the leafset, so we can
                // call removeFromLeafsetIfPossible() in leafSetChange()
                leafSet.put(rls.returnHandle());

                // nodes were never coming back alive when we called them faulty incorrectly
                if (!rls.returnHandle().isAlive()) {
                    logger.info("Issued lease to dead node:" + rls.returnHandle() + " initiating checkLiveness()");
                    rls.returnHandle().checkLiveness();

                }
            }

            // I don't like that we're sending the message after setting lastTimeRenewedLease, becuase there
            // could be a delay between here and above, but this should still be fine, because with
            // the assumption that the clocks are both advancing normally, we still should not get inconsistency
            // because the other node will stop receiving 30 seconds after he requested the lease, and we
            // wont receive until 30 seconds after the lastTimeRenewedLease.put() above
            thePastryNode.send(rls.returnHandle(),
                    new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, rls.getTimeStamp()), null, options);

        } else if (msg instanceof InitiateLeafSetMaintenance) {
            // perform leafset maintenance
            NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

            if (set.size() > 1) {
                NodeHandle handle = set.get(random.nextInt(set.size() - 1) + 1);
                thePastryNode.send(handle,
                        new RequestLeafSet(localHandle, timeSource.currentTimeMillis()), null, options);
                thePastryNode.send(handle,
                        new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, 0), null, options);

                NodeHandle check = set.get(random
                        .nextInt(set.size() - 1) + 1);
                check.checkLiveness();
            }
        } else if (msg instanceof InitiatePingNeighbor) {
            // IPN every 20 seconds
            NodeHandle left = leafSet.get(-1);
            NodeHandle right = leafSet.get(1);

            // send BLS to left/right neighbor
            // ping if we don't currently have a lease
            if (left != null) {
                sendBLS(left, !hasLease(left));
            }
            if (right != null) {
                sendBLS(right, !hasLease(right));
            }
        }
    }

    /**
     * Broadcast the leaf set to all members of the local leaf set.
     */
    protected void broadcastAll() {
        BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet,
                BroadcastLeafSet.JoinAdvertise, 0);
        NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

        for (int i = 1; i < set.size(); i++)
            thePastryNode.send(set.get(i), bls, null, options);
    }

    public void start() {
        if (!hasSetStrategy) {
            logger.info("PLSP.start(): Setting self as ReadyStrategy");
            localNode.setReadyStrategy(this);
            hasSetStrategy = true;
            localNode.addLeafSetListener(this);
            // to notify listeners now if we have proper leases
            isReady();
        }
    }

    public void stop() {
        if (hasSetStrategy) {
            logger.info("PLSP.start(): Removing self as ReadyStrategy");
            hasSetStrategy = false;
            localNode.deleteLeafSetListener(this);
        }
    }

    public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
        NodeHandle newLeft = leafSet.get(-1);
        if (newLeft != null && (lastLeft != newLeft)) {
            lastLeft = newLeft;
            sendBLS(lastLeft, true);
        }
        NodeHandle newRight = leafSet.get(1);
        if (newRight != null && (lastRight != newRight)) {
            lastRight = newRight;
            sendBLS(lastRight, true);
        }
    }

    /**
     *
     */
    public boolean isReady() {
        // check to see if we've heard from the left/right neighbors recently enough
        boolean shouldBeReady = shouldBeReady(); // temp

        if (shouldBeReady != ready) {
            thePastryNode.setReady(shouldBeReady); // will call back in to setReady() and notify the observers
        }

        return shouldBeReady;
    }

//  HashSet deadLeases = new HashSet();

    public void setReady(boolean r) {
        if (ready != r) {
            synchronized (thePastryNode) {
                ready = r;
            }
            thePastryNode.notifyReadyObservers();
        }
    }

    public boolean shouldBeReady() {

        NodeHandle left = leafSet.get(-1);
        NodeHandle right = leafSet.get(1);

        // do it this way so we get going on both leases if need be
        boolean ret = true;

        // see if received BLS within past LEASE_PERIOD seconds from left neighbor
        if (!hasLease(left)) {
            ret = false;
            sendBLS(left, true);
        }

        // see if received BLS within past LEASE_PERIOD seconds from right neighbor
        if (!hasLease(right)) {
            ret = false;
            sendBLS(right, true);
        }

//    if (deadLeases.size() > 0) return false;
        return ret;
    }

    /**
     * Do we have a lease from this node?
     * <p>
     * Returns true if nh is null.
     *
     * @param nh the NodeHandle we are interested if we have a lease from
     * @return if we have a lease from the NodeHandle
     */
    public boolean hasLease(NodeHandle nh) {
        long curTime = timeSource.currentTimeMillis();
        long leaseOffset = curTime - LEASE_PERIOD;

        if (nh != null) {
            Long time = lastTimeReceivedBLS.get(nh);
            // we don't have a lease
            return time != null && (time >= leaseOffset);
        }
        return true;
    }

    /**
     * @param sendTo
     * @return true if we sent it, false if we didn't because of throttled
     */
    private boolean sendBLS(NodeHandle sendTo, boolean checkLiveness) {
        Long time = lastTimeSentBLS.get(sendTo);
        long currentTime = timeSource.currentTimeMillis();
        if (time == null || (time < (currentTime - BLS_THROTTLE))) {
            logger.debug("PeriodicLeafSetProtocol: Checking liveness on neighbor:" + sendTo + " " + time + " cl:" + checkLiveness);
            lastTimeSentBLS.put(sendTo, currentTime);

            thePastryNode.send(sendTo, new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, 0), null, options);
            thePastryNode.send(sendTo, new RequestLeafSet(localHandle, currentTime), null, options);
            if (checkLiveness) {
                sendTo.checkLiveness();
            }
            return true;
        }
        return false;
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

    public void destroy() {
        logger.info("PLSP: destroy() called");
        destroyed = true;
        if (pingNeighborMessage != null)
            pingNeighborMessage.cancel();
        pingNeighborMessage = null;
        lastLeft = null;
        lastRight = null;
        lastTimeReceivedBLS.clear();
        lastTimeRenewedLease.clear();
        lastTimeSentBLS.clear();
//    deadLeases.clear();
    }

    @Override
    public void leafSetChange(NodeHandle nh, boolean wasAdded) {
        super.leafSetChange(nh, wasAdded);
//    logger.log("leafSetChange("+nh+","+wasAdded+")");
        if (wasAdded) {
            nh.addObserver(this, 50);
            if (!nh.isAlive()) {
                // nh.checkLiveness(); // this is done in all of the calling code (in this Protocol)
                removeFromLeafsetIfPossible(nh);
            }
        } else {
            logger.debug("Removed " + nh + " from the LeafSet.");
            nh.deleteObserver(this);
        }
    }

    /**
     * Only remove the item if you did not give a lease.
     */
    public void update(final Observable o, final Object arg) {
        if (destroyed) return;
        if (o instanceof NodeHandle) {
            if (arg == NodeHandle.DECLARED_DEAD) {
                removeFromLeafsetIfPossible((NodeHandle) o);
            }
            return;
        }

        // this is if we are the "seed" node
        if (o instanceof PastryNode) {
            if (arg instanceof Boolean) { // could also be a JoinFailedException
                Boolean rdy = (Boolean) arg;
                if (rdy.equals(Boolean.TRUE)) {
                    localNode.deleteObserver(this);
                    start();
                }
            }
        }
    }

    public void removeFromLeafsetIfPossible(final NodeHandle nh) {
        if (nh.isAlive()) return;
        Long l_time = lastTimeRenewedLease.get(nh);
        if (l_time == null) {
            // there is no lease on record
            leafSet.remove(nh);
        } else {
            // verify doesn't have a current lease
            long leaseExpiration = l_time + LEASE_PERIOD;
            long now = timeSource.currentTimeMillis();
            if (leaseExpiration > now) {
                logger.info("Removing " + nh + " from leafset later." + (leaseExpiration - now));
                // remove it later when lease expries
                thePastryNode.getEnvironment().getSelectorManager().getTimer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        logger.debug("removeFromLeafsetIfPossible(" + nh + ")");
                        // do this recursively in case we issue a new lease
                        removeFromLeafsetIfPossible(nh);
                    }
                }, leaseExpiration - now);
            } else {
                // lease has expired
                leafSet.remove(nh);
            }
        }
    }

    public static class PLSPMessageDeserializer extends PJavaSerializedDeserializer {

        public PLSPMessageDeserializer(PastryNode pn) {
            super(pn);
        }

        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            switch (type) {
                case RequestLeafSet.TYPE:
                    return new RequestLeafSet(sender, buf);
                case BroadcastLeafSet.TYPE:
                    return new BroadcastLeafSet(buf, pn);
            }
            return null;
        }

    }
}
