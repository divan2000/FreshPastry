package org.urajio.freshpastry.rice.pastry.pns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.exception.TimeoutException;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.util.AttachableCancellable;
import org.urajio.freshpastry.rice.p2p.util.tuples.MutableTuple;
import org.urajio.freshpastry.rice.p2p.util.tuples.Tuple;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.pns.messages.LeafSetRequest;
import org.urajio.freshpastry.rice.pastry.pns.messages.LeafSetResponse;
import org.urajio.freshpastry.rice.pastry.pns.messages.RouteRowRequest;
import org.urajio.freshpastry.rice.pastry.pns.messages.RouteRowResponse;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;
import org.urajio.freshpastry.rice.pastry.standard.ProximityNeighborSelector;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;
import org.urajio.freshpastry.rice.selector.Timer;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.util.*;

/**
 * Can request LeafSet, RouteRow, Proximity of nodes, implements the PNS algorithm.
 * <p>
 * Flow:
 * call getLeafSet(...)
 * addToWaitingForLeafSet()
 * <p>
 * cancellable.cancel()
 * removeFromWaitingForLeafSet()
 * <p>
 * <p>
 * TODO: Make use the environment's clock for the wait() calls.
 *
 * @author Jeff Hoye
 */
public class PNSApplication extends PastryAppl implements ProximityNeighborSelector, ProximityListener<NodeHandle> {
    public static final int DEFAULT_PROXIMITY = ProximityProvider.DEFAULT_PROXIMITY;
    private final static Logger logger = LoggerFactory.getLogger(PNSApplication.class);
    /**
     * Hashtable which keeps track of temporary ping values, which are
     * only used during the getNearest() method
     */
    protected final Map<NodeHandle, Integer> pingCache = new HashMap<>();

    protected final byte rtBase;
    final short depth; // = (Id.IdBitLength / rtBase);
    final Map<NodeHandle, Collection<Tuple<Continuation<LeafSet, Exception>, Cancellable>>> waitingForLeafSet =
            new HashMap<>();
    final Map<NodeHandle, Collection<Tuple<Continuation<RouteSet[], Exception>, Cancellable>>[]> waitingForRouteRow =
            new HashMap<>();
    final Map<NodeHandle, Collection<Continuation<Integer, IOException>>> waitingForPing =
            new HashMap<>();
    protected Environment environment;
    protected Timer timer;

    public PNSApplication(PastryNode pn) {
        super(pn, null, 0, null);
        setDeserializer(new PNSDeserializer());
        this.environment = pn.getEnvironment();
        rtBase = (byte) environment.getParameters().getInt("pastry_rtBaseBitLength");
        depth = (short) (Id.IdBitLength / rtBase);
    }

    /**
     * We always want to receive messages.
     */
    public boolean deliverWhenNotReady() {
        return true;
    }

    @Override
    public void messageForAppl(Message msg) {
        logger.debug("messageForAppl(" + msg + ")");

        if (msg instanceof LeafSetRequest) {
            LeafSetRequest req = (LeafSetRequest) msg;
            thePastryNode.send(req.getSender(), new LeafSetResponse(thePastryNode.getLeafSet(), getAddress()), null, null);
            return;
        }

        if (msg instanceof LeafSetResponse) {
            LeafSetResponse response = (LeafSetResponse) msg;
            synchronized (waitingForLeafSet) {
                LeafSet ls = response.leafset;
                Collection<Tuple<Continuation<LeafSet, Exception>, Cancellable>> waiters = waitingForLeafSet.remove(ls.get(0));
                if (waiters != null) {
                    for (Tuple<Continuation<LeafSet, Exception>, Cancellable> w : waiters) {
                        w.b().cancel();
                        w.a().receiveResult(ls);
                    }
                }
            }
            return;
        }

        if (msg instanceof RouteRowRequest) {
            RouteRowRequest req = (RouteRowRequest) msg;
            thePastryNode.send(req.getSender(),
                    new RouteRowResponse(
                            thePastryNode.getLocalHandle(),
                            req.index,
                            thePastryNode.getRoutingTable().getRow(req.index), getAddress()), null, null);
            return;
        }

        if (msg instanceof RouteRowResponse) {
            RouteRowResponse response = (RouteRowResponse) msg;
            synchronized (waitingForRouteRow) {
                Collection<Tuple<Continuation<RouteSet[], Exception>, Cancellable>>[] waiters = waitingForRouteRow.get(response.getSender());
                if (waiters != null) {
                    if (waiters[response.index] != null) {
                        for (Tuple<Continuation<RouteSet[], Exception>, Cancellable> w : new ArrayList<>(waiters[response.index])) {
                            w.b().cancel();
                            w.a().receiveResult(response.row);
                        }
                        waiters[response.index].clear();
                        waiters[response.index] = null;

                        // remove the entry if all rows empty
                        boolean deleteIt = true;
                        for (int i = 0; i < depth; i++) {
                            if (waiters[i] != null) {
                                deleteIt = false;
                                break;
                            }
                        }

                        if (deleteIt) waitingForRouteRow.remove(response.getSender());
                    }
                }
            }
            return;
        }

        logger.warn("unrecognized message in messageForAppl(" + msg + ")");
    }

    public Cancellable getNearHandles(final Collection<NodeHandle> bootHandles, final Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
        if (bootHandles == null || bootHandles.size() == 0 || bootHandles.iterator().next() == null) {
            deliverResultToMe.receiveResult(bootHandles);
            return null;
        }

        final AttachableCancellable ret = new AttachableCancellable();

        // when this goes empty, return what we have
        final Collection<NodeHandle> remaining = new HashSet<>(bootHandles);

        thePastryNode.addProximityListener(this);

        // our best candidate so far, initiall null
        final MutableTuple<NodeHandle, Cancellable> best = new MutableTuple<>();

        // get the proximity of everyone in the list
        for (NodeHandle nh : bootHandles) {
            final NodeHandle handle = nh;
            Continuation<Integer, IOException> c = new Continuation<Integer, IOException>() {

                public void receiveResult(Integer result) {
                    logger.debug("got proximity for " + handle + " in getNearHandles()");
                    if ((best.a() != null) &&
                            (pingCache.get(best.a()) < result)) {
                        // we're not the best, just return
                        return;
                    }

                    // we are the best

                    // cancel the last task
                    if (best.b() != null) best.b().cancel();

                    // set ourself as best
                    Cancellable cancellable = getNearest(handle, new Continuation<Collection<NodeHandle>, Exception>() {

                        public void receiveResult(Collection<NodeHandle> result) {
                            logger.debug("receiveResult(" + result + ") in getNearHandles()");
                            ret.cancel(); // go ahead and cancel everything
                            finish();
                        }

                        public void receiveException(Exception exception) {
                            logger.debug("PNS got an exception in getNearHandles() returning what we got.", exception);
                            finish();
                        }

                        public void finish() {
                            thePastryNode.removeProximityListener(PNSApplication.this);
                            List<NodeHandle> ret = sortedProximityCache();
                            purgeProximityCache();
                            logger.info("getNearHandles(" + bootHandles + "):" + ret.size() + ret);
                            deliverResultToMe.receiveResult(getNearHandlesHelper(ret));
                        }
                    });

                    ret.attach(cancellable);
                    best.set(handle, cancellable);
                }

                public void receiveException(IOException exception) {
                    remaining.remove(handle);
                }
            };
            getProximity(handle, c, 10000); // TODO: Make configurable
        }
        return ret;
    }

    /**
     * Helper for getNearHandles
     * <p>
     * Can be overridden to select out any handles that shouldn't be returned.
     *
     * @param handles
     * @return
     */
    protected List<NodeHandle> getNearHandlesHelper(List<NodeHandle> handles) {
        return handles;
    }

    /**
     * This method returns the remote leafset of the provided handle
     * to the caller, in a protocol-dependent fashion.  Note that this method
     * may block while sending the message across the wire.
     * <p>
     * Non-blocking version.
     *
     * @param handle The node to connect to
     * @param c      Continuation to return the LeafSet to
     * @return
     * @throws IOException
     */
    public Cancellable getLeafSet(final NodeHandle handle, final Continuation<LeafSet, Exception> c) {
        final AttachableCancellable cancellable = new AttachableCancellable() {
            public boolean cancel() {
                removeFromWaitingForLeafSet(handle, c);
                super.cancel();

                // it was cancelled if it was removed from the list, and if not then it was already cancelled
                return true;
            }
        };

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                cancellable.cancel();
                c.receiveException(new TimeoutException("Ping to " + handle + " timed out."));
            }
        };

        addToWaitingForLeafSet(handle, c, task);
        cancellable.attach(task);

        cancellable.attach(thePastryNode.send(handle, new LeafSetRequest(this.getNodeHandle(), this.getAddress()), new PMessageNotification() {
            public void sent(PMessageReceipt msg) {
            }

            public void sendFailed(PMessageReceipt msg, Exception reason) {
                cancellable.cancel();
//        removeFromWaitingForLeafSet(handle, c);
                c.receiveException(reason);
            }
        }, null));

        return cancellable;
    }

    protected void addToWaitingForLeafSet(NodeHandle handle, Continuation<LeafSet, Exception> c, Cancellable cancelMeWhenSuccess) {
        synchronized (waitingForLeafSet) {
            Collection<Tuple<Continuation<LeafSet, Exception>, Cancellable>> waiters = waitingForLeafSet.get(handle);
            if (waiters == null) {
                waiters = new ArrayList<>();
                waitingForLeafSet.put(handle, waiters);
            }
            waiters.add(new Tuple<>(c, cancelMeWhenSuccess));
        }
    }

    protected boolean removeFromWaitingForLeafSet(NodeHandle handle, Continuation<LeafSet, Exception> c) {
        synchronized (waitingForLeafSet) {
            Collection<Tuple<Continuation<LeafSet, Exception>, Cancellable>> waiters = waitingForLeafSet.get(handle);
            if (waiters == null) return false;

            // look for and remove c in waiters
            boolean ret = false;
            Iterator<Tuple<Continuation<LeafSet, Exception>, Cancellable>> i = waiters.iterator();
            while (i.hasNext()) {
                Tuple<Continuation<LeafSet, Exception>, Cancellable> foo = i.next();
                if (foo.a().equals(c)) {
                    ret = true;
                    foo.b().cancel();
                    i.remove();
                }
            }

            if (waiters.isEmpty()) waitingForLeafSet.remove(handle);
            return ret;
        }
    }

    /**
     * Non-blocking version.
     *
     * @param handle
     * @param row
     * @param c
     * @return
     * @throws IOException
     */
    public Cancellable getRouteRow(final NodeHandle handle, final short row, final Continuation<RouteSet[], Exception> c) {
        final AttachableCancellable cancellable = new AttachableCancellable() {
            public boolean cancel() {
                removeFromWaitingForRouteRow(handle, row, c);
                super.cancel();

                // it was cancelled if it was removed from the list, and if not then it was already cancelled
                return true;
            }
        };

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                cancellable.cancel();
                c.receiveException(new TimeoutException("Ping to " + handle + " timed out."));
            }
        };

        addToWaitingForRouteRow(handle, row, c, task);
        cancellable.attach(task);

        cancellable.attach(thePastryNode.send(handle, new RouteRowRequest(this.getNodeHandle(), row, this.getAddress()), new PMessageNotification() {
            public void sent(PMessageReceipt msg) {
            }

            public void sendFailed(PMessageReceipt msg, Exception reason) {
//        removeFromWaitingForRouteRow(handle, row, c);
                cancellable.cancel();
                c.receiveException(reason);
            }
        }, null));

        return cancellable;
    }

    protected void addToWaitingForRouteRow(NodeHandle handle, int row, Continuation<RouteSet[], Exception> c, Cancellable cancelMeWhenSuccess) {
        synchronized (waitingForRouteRow) {
            Collection<Tuple<Continuation<RouteSet[], Exception>, Cancellable>>[] waiters = waitingForRouteRow.get(handle);
            if (waiters == null) {
                waiters = new Collection[depth];
                waitingForRouteRow.put(handle, waiters);
            }
            if (waiters[row] == null) {
                waiters[row] = new ArrayList<>();
            }
            waiters[row].add(new Tuple<>(c, cancelMeWhenSuccess));
        }
    }


    /**
     * This method determines and returns the proximity of the current local
     * node the provided NodeHandle.  This will need to be done in a protocol-
     * dependent fashion and may need to be done in a special way.
     * <p>
     * Timeout of 5 seconds.
     *
     * @param handle The handle to determine the proximity of
     * @return The proximity of the provided handle
     */
//  public int getProximity(NodeHandle handle) {    
//    final int[] container = new int[1];
//    container[0] = DEFAULT_PROXIMITY;
//    synchronized (container) {
//      getProximity(handle, new Continuation<Integer, IOException>() {      
//        public void receiveResult(Integer result) {
//          synchronized(container) {
//            container[0] = result.intValue();
//            container.notify();
//          }
//        }
//      
//        public void receiveException(IOException exception) {
//          synchronized(container) {
//            container.notify();
//          }
//        }      
//      });
//      
//      if (container[0] == DEFAULT_PROXIMITY) {
//        try {
//          container.wait(5000);
//        } catch(InterruptedException ie) {
//          // continue to return        
//        }
//      }
//    }
//    if (logger.level <= Logger.FINE) logger.log("getProximity("+handle+") returning "+container[0]);
//    return container[0];
//  }
    protected boolean removeFromWaitingForRouteRow(NodeHandle handle, int row, Continuation<RouteSet[], Exception> c) {
        synchronized (waitingForRouteRow) {
            Collection<Tuple<Continuation<RouteSet[], Exception>, Cancellable>>[] waiters = waitingForRouteRow.get(handle);
            if (waiters == null) return false;
            if (waiters[row] == null) return false;
            boolean ret = false;
            Iterator<Tuple<Continuation<RouteSet[], Exception>, Cancellable>> it = waiters[row].iterator();
            while (it.hasNext()) {
                Tuple<Continuation<RouteSet[], Exception>, Cancellable> foo = it.next();
                if (foo.a().equals(c)) {
                    ret = true;
                    foo.b().cancel();
                    it.remove();
                }
            }

            // remove the row if empty
            if (waiters[row].isEmpty()) {
                waiters[row] = null;
            }

            // remove the entry if all rows empty
            boolean deleteIt = true;
            for (int i = 0; i < depth; i++) {
                if (waiters[i] != null) {
                    deleteIt = false;
                    break;
                }
            }

            if (deleteIt) waitingForRouteRow.remove(handle);

            return ret;
        }
    }

    /**
     * Non-blocking version, no timeout.
     * <p>
     * TODO: Make this fail early if faulty.
     *
     * @param handle
     * @param c
     */
    public Cancellable getProximity(final NodeHandle handle, final Continuation<Integer, IOException> c, int timeout) {
        logger.debug("getProximity(" + handle + ")");
        int prox;

        // acquire a lock that will block proximityChanged()
        synchronized (waitingForPing) {
            // see what we have for proximity (will initiate a checkLiveness => proximityChanged() if DEFAULT)
            prox = thePastryNode.proximity(handle);
            if (prox == DEFAULT_PROXIMITY) {
                logger.debug("getProximity(" + handle + "): waiting for proximity update");
                // need to find it
                Collection<Continuation<Integer, IOException>> waiters = waitingForPing.get(handle);
                if (waiters == null) {
                    waiters = new ArrayList<>();
                    waitingForPing.put(handle, waiters);
                }
                waiters.add(c);

                final AttachableCancellable cancellable = new AttachableCancellable() {
                    public boolean cancel() {
                        synchronized (waitingForPing) {
                            Collection<Continuation<Integer, IOException>> waiters = waitingForPing.get(handle);
                            if (waiters != null) {
                                waiters.remove(c);
                                if (waiters.isEmpty()) {
                                    waitingForPing.remove(handle);
                                }
                            }
                        }
                        super.cancel();

                        // it was cancelled if it was removed from the list, and if not then it was already cancelled
                        return true;
                    }
                };

                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        cancellable.cancel();
                        c.receiveException(new TimeoutException("Ping to " + handle + " timed out."));
                    }
                };

                cancellable.attach(task);
                environment.getSelectorManager().schedule(task, timeout);
                return cancellable;
            }

            // else we have the proximity, no need to wait for it, just return the correct result
        }

        synchronized (pingCache) {
            pingCache.put(handle, prox);
        }

        // we already had the right proximity
        c.receiveResult(prox);
        return null;
    }

    public void proximityChanged(NodeHandle i, int newProximity, Map<String, Object> options) {
        logger.debug("proximityChanged(" + i + "," + newProximity + ")");
        synchronized (pingCache) {
            pingCache.put(i, newProximity);
        }
        synchronized (waitingForPing) {
            if (waitingForPing.containsKey(i)) {
                Collection<Continuation<Integer, IOException>> waiting = waitingForPing.remove(i);
                for (Continuation<Integer, IOException> c : waiting) {
                    c.receiveResult(newProximity);
                }
            }
        }
    }


    private void purgeProximityCache() {
        pingCache.clear();
    }

    public List<NodeHandle> sortedProximityCache() {
        ArrayList<NodeHandle> handles = new ArrayList<>(pingCache.keySet());
        handles.sort(new Comparator<NodeHandle>() {

            public int compare(NodeHandle a, NodeHandle b) {
                return pingCache.get(a) - pingCache.get(b);
            }
        });

        return handles;
    }

    /**
     * This method implements the algorithm in the Pastry locality paper
     * for finding a close node the the current node through iterative
     * leafset and route row requests.  The seed node provided is any
     * node in the network which is a member of the pastry ring.  This
     * algorithm is designed to work in a protocol-independent manner, using
     * the getResponse(Message) method provided by subclasses.
     *
     * @param seed Any member of the pastry ring
     * @return A node suitable to boot off of (which is close the this node)
     */
    public Cancellable getNearest(final NodeHandle seed, final Continuation<Collection<NodeHandle>, Exception> retToMe) {
//    try {
        logger.debug("getNearest(" + seed + ")");
        // if the seed is null, we can't do anything
        if (seed == null) {
            logger.warn("getNearest(" + seed + ")", new Exception("Stack Trace"));
            environment.getSelectorManager().invoke(new Runnable() {
                public void run() {
                    retToMe.receiveResult(null);
                }
            });
            return null;
        }

        final AttachableCancellable ret = new AttachableCancellable();

        // get closest node in leafset
        ret.attach(getLeafSet(seed,
                new Continuation<LeafSet, Exception>() {

                    public void receiveResult(LeafSet result) {
                        // ping everyone in the leafset:
                        logger.debug("getNearest(" + seed + ") got " + result);

                        // seed is the bootstrap node that we use to enter the pastry ring
                        NodeHandle nearNode = seed;
                        NodeHandle currentClosest = seed;

                        ret.attach(closestToMe(nearNode, result, new Continuation<NodeHandle, Exception>() {

                            /**
                             * Now we have the closest node in the leafset.
                             */
                            public void receiveResult(NodeHandle result) {
                                NodeHandle nearNode = result;
                                // get the number of rows in a routing table
                                // -- Here, we're going to be a little inefficient now.  It doesn't
                                // -- impact correctness, but we're going to walk up from the bottom
                                // -- of the routing table, even through some of the rows are probably
                                // -- unfilled.  We'll optimize this in a later iteration.
                                short i = 0;

                                // make "ALL" work
                                if (!environment.getParameters().getString("pns_num_rows_to_use").equalsIgnoreCase("all")) {
                                    i = (short) (depth - (short) (environment.getParameters().getInt("pns_num_rows_to_use")));
                                }

                                // fix it up to not throw an error if the number is too big
                                if (i < 0) i = 0;

                                ret.attach(seekThroughRouteRows(i, depth, nearNode, new Continuation<NodeHandle, Exception>() {

                                    public void receiveResult(NodeHandle result) {
                                        NodeHandle nearNode = result;
                                        retToMe.receiveResult(sortedProximityCache());
                                    }

                                    public void receiveException(Exception exception) {
                                        retToMe.receiveResult(sortedProximityCache());
                                    }
                                }));
                            }

                            public void receiveException(Exception exception) {
                                retToMe.receiveResult(sortedProximityCache());
                            }
                        }));
                    }

                    public void receiveException(Exception exception) {
                        retToMe.receiveException(exception);
                    }
                }));

        return ret;
    }


    /**
     * This method recursively seeks through the routing tables of the other nodes, always taking the closest node.
     * When it gets to the top, it keeps taking the top row of the cloest node.  Tail recursion with the continuation.
     */
    private Cancellable seekThroughRouteRows(final short i, final short depth, final NodeHandle currentClosest,
                                             final Continuation<NodeHandle, Exception> returnToMe) {
        final AttachableCancellable ret = new AttachableCancellable();

        // base case
        ret.attach(getRouteRow(currentClosest, i, new Continuation<RouteSet[], Exception>() {
            public void receiveResult(RouteSet[] result) {
                ret.attach(closestToMe(currentClosest, result, new Continuation<NodeHandle, Exception>() {
                    public void receiveResult(NodeHandle nearNode) {
                        if ((i >= depth - 1) && (currentClosest.equals(nearNode))) {
                            // base case
                            // i == depth and we didn't find a closer node
                            returnToMe.receiveResult(nearNode);
                        } else {
                            // recursive case
                            short newIndex = (short) (i + 1);
                            if (newIndex > depth - 1) newIndex = (short) (depth - 1);
                            seekThroughRouteRows(newIndex, depth, nearNode, returnToMe);
                        }
                    }

                    public void receiveException(Exception exception) {
                        returnToMe.receiveException(exception);
                    }
                }));

            }

            public void receiveException(Exception exception) {
                returnToMe.receiveException(exception);
            }

        }));

        return ret;
    }


    /**
     * This method returns the closest node to the current node out of
     * the union of the provided handle and the node handles in the
     * leafset
     *
     * @param handle  The handle to include
     * @param leafSet The leafset to include
     * @return The closest node out of handle union leafset
     */
    private Cancellable closestToMe(NodeHandle handle, LeafSet leafSet, Continuation<NodeHandle, Exception> c) {
        if (leafSet == null) {
            c.receiveResult(handle);
            return null;
        }
        HashSet<NodeHandle> handles = new HashSet<>();

        for (int i = 1; i <= leafSet.cwSize(); i++)
            handles.add(leafSet.get(i));

        for (int i = -leafSet.ccwSize(); i < 0; i++)
            handles.add(leafSet.get(i));

        return closestToMe(handle, handles, c);
    }

    /**
     * This method returns the closest node to the current node out of
     * the union of the provided handle and the node handles in the
     * routeset
     */
    private Cancellable closestToMe(NodeHandle handle, RouteSet[] routeSets, Continuation<NodeHandle, Exception> c) {
        ArrayList<NodeHandle> handles = new ArrayList<>();

        for (RouteSet set : routeSets) {
            if (set != null) {
                for (int j = 0; j < set.size(); j++)
                    handles.add(set.get(j));
            }
        }

        return closestToMe(handle, handles, c);
    }

    /**
     * This method returns the closest node to the current node out of
     * the union of the provided handle and the node handles in the
     * array
     *
     * @param handle  The handle to include
     * @param handles The array to include
     * @return The closest node out of handle union array
     */
    private Cancellable closestToMe(final NodeHandle handle, final Collection<NodeHandle> handles, final Continuation<NodeHandle, Exception> c) {
        logger.debug("closestToMe(" + handle + "," + handles + ")");
        final AttachableCancellable ret = new AttachableCancellable();
        final NodeHandle[] closestNode = {handle};

        final Collection<NodeHandle> remaining = new HashSet<>(handles);
        if (!remaining.contains(handle)) remaining.add(handle);

        // shortest distance found till now
        final int[] nearestdist = {Integer.MAX_VALUE}; //proximity(closestNode);

        ArrayList<NodeHandle> temp = new ArrayList<>(remaining); // need to include handle when looping
        for (NodeHandle nh : temp) {
            final NodeHandle tempNode = nh;
            logger.debug("closestToMe checking prox on " + tempNode + "(" + handle + "," + handles + ")");
            ret.attach(getProximity(handle, new Continuation<Integer, IOException>() {

                public void receiveResult(Integer result) {
                    logger.debug("closestToMe got prox(" + result + ") on " + tempNode + "(" + handle + "," + handles + ")");
                    remaining.remove(tempNode);
                    int prox = result;
                    if ((prox >= 0) && (prox < nearestdist[0]) && tempNode.isAlive()) {
                        nearestdist[0] = prox;
                        closestNode[0] = tempNode;
                    }
                    finish();
                }

                public void receiveException(IOException exception) {
                    remaining.remove(tempNode);
                    finish();
                }

                public void finish() {
                    if (remaining.isEmpty()) {
                        ret.cancel();
                        c.receiveResult(closestNode[0]);
                    }
                }
            }, 10000));
        }

        return ret;
    }


    class PNSDeserializer implements MessageDeserializer {
        public org.urajio.freshpastry.rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority, org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle sender) throws IOException {
            switch (type) {
                case LeafSetRequest.TYPE:
                    return LeafSetRequest.build(buf, (NodeHandle) sender, getAddress());
                case LeafSetResponse.TYPE:
                    return LeafSetResponse.build(buf, thePastryNode, getAddress());
                case RouteRowRequest.TYPE:
                    return RouteRowRequest.build(buf, (NodeHandle) sender, getAddress());
                case RouteRowResponse.TYPE:
                    return new RouteRowResponse(buf, thePastryNode, (NodeHandle) sender, getAddress());
            }
            return null;
        }

    }
}
