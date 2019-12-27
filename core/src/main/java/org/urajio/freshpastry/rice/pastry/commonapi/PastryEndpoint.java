package org.urajio.freshpastry.rice.pastry.commonapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.Executable;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.*;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.NodeSet;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessageNotification;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;
import org.urajio.freshpastry.rice.pastry.routing.SendOptions;
import org.urajio.freshpastry.rice.pastry.standard.StandardAddress;

import java.io.IOException;
import java.util.*;

/**
 * This class serves as gluecode, which allows applications written for the common
 * API to work with pastry.
 *
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 */
public class PastryEndpoint extends PastryAppl implements Endpoint {
    private final static Logger logger = LoggerFactory.getLogger(PastryEndpoint.class);

    protected Application application;
    /**
     * The commonapi's deserializer.  Java Deserializer by default.
     */
    MessageDeserializer appDeserializer;
    boolean consistentRouting = true;

    /**
     * Constructor.
     *
     * @param pn the pastry node that the application attaches to.
     */
    public PastryEndpoint(PastryNode pn, Application application, String instance, boolean register) {
        this(pn, application, instance, 0, register);
    }


    /**
     * Constructor.
     *
     * @param pn the pastry node that the application attaches to.
     */
//  public PastryEndpoint(PastryNode pn, Application application, int address) {
//    this(pn, application, "[PORT " + address + "]", address, true);
//  }
    public PastryEndpoint(PastryNode pn, Application application, String instance, int address, boolean register) {
        super(pn,
                instance,
                address == 0 ? StandardAddress.getAddress(application.getClass(), instance, pn.getEnvironment()) : address,
                null);

        appDeserializer = deserializer; // use this as the apps default deserializer
        deserializer = new PEDeserializer();
        this.application = application;
        if (register)
            register();
    }

    // API methods to be invoked by applications

    /**
     * Returns this node's id, which is its identifier in the namespace.
     *
     * @return The local node's id
     */
    public Id getId() {
        return thePastryNode.getNodeId();
    }

    /**
     * This operation forwards a message towards the root of
     * key.  The optional hint argument specifies a node
     * that should be used as a first hop in routing the message. A
     * good hint, e.g. one that refers to the key's current root, can
     * result in the message being delivered in one hop; a bad hint
     * adds at most one extra hop to the route. Either K or hint may
     * be NULL, but not both.  The operation provides a best-effort
     * service: the message may be lost, duplicated, corrupted, or
     * delayed indefinitely.
     *
     * @param key  the key
     * @param msg  the message to deliver.
     * @param hint the hint
     */
    public MessageReceipt route(Id key, Message msg, NodeHandle hint) {
        return route(key, msg, hint, null);
    }

    public MessageReceipt route(Id key, Message msg, NodeHandle hint, DeliveryNotification deliverAckToMe) {
        return route(key, msg, hint, deliverAckToMe, null);
    }

    public MessageReceipt route(Id key, Message msg, NodeHandle hint, DeliveryNotification deliverAckToMe, Map<String, Object> options) {
        logger.debug("[" + thePastryNode + "] route " + msg + " to " + key);

        PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), msg, thePastryNode.getLocalHandle());
        return routeHelper(key, pm, hint, deliverAckToMe, options);
    }


    /**
     * This duplication of the above code is to make a fast path for the RawMessage.  Though the codeblock
     * looks identical, it is acually calling a different PEM constructor.
     */
    public MessageReceipt route(Id key, RawMessage msg, NodeHandle hint) {
        return route(key, msg, hint, null);
    }

    public MessageReceipt route(Id key, RawMessage msg, NodeHandle hint, DeliveryNotification deliverAckToMe) {
        return route(key, msg, hint, deliverAckToMe, null);
    }

    public MessageReceipt route(Id key, RawMessage msg, NodeHandle hint, DeliveryNotification deliverAckToMe, Map<String, Object> options) {
        logger.debug("[" + thePastryNode + "] route " + msg + " to " + key);

        PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), msg, thePastryNode.getLocalHandle());
        return routeHelper(key, pm, hint, deliverAckToMe, options);
    }

    /**
     * This method:
     * a) constructs the RouteMessage
     * b) adds a MessageReceipt to the RouteMessage
     * c) sets the priority option
     * d) calls router.route(rm);
     *
     * @param key
     * @param pm
     * @param hint
     * @param deliverAckToMe
     * @return
     */
    private MessageReceipt routeHelper(Id key, final PastryEndpointMessage pm, final NodeHandle hint, final DeliveryNotification deliverAckToMe, Map<String, Object> options) {
        if (options == null) options = this.options;
        logger.debug("routeHelper(" + key + "," + pm + "," + hint + "," + deliverAckToMe + ").init()");

        if ((key == null) && (hint == null)) {
            throw new IllegalArgumentException("key and hint are null!");
        }
        boolean noKey = false;
        if (key == null) {
            noKey = true;
            key = hint.getId();
        }

        final org.urajio.freshpastry.rice.pastry.routing.RouteMessage rm =
                new org.urajio.freshpastry.rice.pastry.routing.RouteMessage(
                        (org.urajio.freshpastry.rice.pastry.Id) key,
                        pm,
                        (org.urajio.freshpastry.rice.pastry.NodeHandle) hint,
                        (byte) thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));

        rm.setPrevNode(thePastryNode.getLocalHandle());
        if (noKey) {
            rm.getOptions().setMultipleHopsAllowed(false);
            rm.setDestinationHandle((org.urajio.freshpastry.rice.pastry.NodeHandle) hint);
        }


        final Id final_key = key;
        // TODO: make PastryNode have a router that does this properly, rather than receiveMessage
        final MessageReceipt ret = new MessageReceipt() {

            public boolean cancel() {
                logger.debug("routeHelper(" + final_key + "," + pm + "," + hint + "," + deliverAckToMe + ").cancel()");
                return rm.cancel();
            }

            public Message getMessage() {
                return pm.getMessage();
            }

            public Id getId() {
                return final_key;
            }

            public NodeHandle getHint() {
                return hint;
            }
        };

        // NOTE: Installing this anyway if the LogLevel is high enough is kind of wild, but really useful for debugging
        if ((deliverAckToMe != null) || (logger.isDebugEnabled())) {
            rm.setRouteMessageNotification(new RouteMessageNotification() {
                public void sendSuccess(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, org.urajio.freshpastry.rice.pastry.NodeHandle nextHop) {
                    logger.debug("routeHelper(" + final_key + "," + pm + "," + hint + "," + deliverAckToMe + ").sendSuccess():" + nextHop);
                    if (deliverAckToMe != null) deliverAckToMe.sent(ret);
                }

                public void sendFailed(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, Exception e) {
                    logger.debug("routeHelper(" + final_key + "," + pm + "," + hint + "," + deliverAckToMe + ").sendFailed(" + e + ")");
                    if (deliverAckToMe != null) deliverAckToMe.sendFailed(ret, e);
                }
            });
        }

//    Map<String, Object> rOptions;
//    if (options == null) {
//      rOptions = new HashMap<String, Object>(); 
//    } else {
//      rOptions = new HashMap<String, Object>(options);
//    }
//    rOptions.put(PriorityTransportLayer.OPTION_PRIORITY, pm.getPriority());

        rm.setTLOptions(OptionsFactory.addOption(options, PriorityTransportLayer.OPTION_PRIORITY, pm.getPriority()));

        thePastryNode.getRouter().route(rm);

        return ret;
    }


    /**
     * Schedules a message to be delivered to this application after the provided number of
     * milliseconds.
     *
     * @param message The message to be delivered
     * @param delay   The number of milliseconds to wait before delivering the message
     */
    public CancellableTask scheduleMessage(Message message, long delay) {
        PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), message, thePastryNode.getLocalHandle());
        return thePastryNode.scheduleMsg(pm, delay);
    }

    /**
     * Schedules a message to be delivered to this application every period number of
     * milliseconds, after delay number of miliseconds have passed.
     *
     * @param message The message to be delivered
     * @param delay   The number of milliseconds to wait before delivering the fist message
     * @param delay   The number of milliseconds to wait before delivering subsequent messages
     */
    public CancellableTask scheduleMessage(Message message, long delay, long period) {
        PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), message, thePastryNode.getLocalHandle());
        return thePastryNode.scheduleMsg(pm, delay, period);
    }

    /**
     * Schedule the specified message for repeated fixed-rate delivery to the
     * local node, beginning after the specified delay. Subsequent executions take
     * place at approximately regular intervals, separated by the specified
     * period.
     *
     * @param msg    a message that will be delivered to the local node after the
     *               specified delay
     * @param delay  time in milliseconds before message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message
     */
    public CancellableTask scheduleMessageAtFixedRate(Message msg,
                                                      long delay, long period) {
        PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), msg, thePastryNode.getLocalHandle());
        return thePastryNode.scheduleMsgAtFixedRate(pm, delay, period);
    }

    /**
     * This method produces a list of nodes that can be used as next
     * hops on a route towards key, such that the resulting route
     * satisfies the overlay protocol's bounds on the number of hops
     * taken.
     * If safe is true, the expected fraction of faulty
     * nodes in the list is guaranteed to be no higher than the
     * fraction of faulty nodes in the overlay; if false, the set may
     * be chosen to optimize performance at the expense of a
     * potentially higher fraction of faulty nodes. This option allows
     * applications to implement routing in overlays with byzantine
     * node failures. Implementations that assume fail-stop behavior
     * may ignore the safe argument.  The fraction of faulty
     * nodes in the returned list may be higher if the safe
     * parameter is not true because, for instance, malicious nodes
     * have caused the local node to build a routing table that is
     * biased towards malicious nodes~\cite{Castro02osdi}.
     *
     * @param key  the message's key
     * @param num  the maximal number of next hops nodes requested
     * @param safe
     * @return the nodehandle set
     */
    public NodeHandleSet localLookup(Id key, int num, boolean safe) {
        // safe ignored until we have the secure routing support

        // get the nodes from the routing table
        NodeHandleSet ret = getRoutingTable().alternateRoutes((org.urajio.freshpastry.rice.pastry.Id) key, num);

        if (ret.size() == 0) {
            // use the leafset
            int index = getLeafSet().mostSimilar((org.urajio.freshpastry.rice.pastry.Id) key);
            NodeHandle nh = getLeafSet().get(index);
            NodeSet set = new NodeSet();
            set.put((org.urajio.freshpastry.rice.pastry.NodeHandle) nh);
            ret = set;
        }

        return ret;
    }

    /**
     * This method produces an unordered list of nodehandles that are
     * neighbors of the local node in the ID space. Up to num
     * node handles are returned.
     *
     * @param num the maximal number of nodehandles requested
     * @return the nodehandle set
     */
    public NodeHandleSet neighborSet(int num) {
        return getLeafSet().neighborSet(num);
    }

    /**
     * This method returns an ordered set of nodehandles on which
     * replicas of the object with key can be stored. The call returns
     * nodes with a rank up to and including max_rank.  If max_rank
     * exceeds the implementation's maximum replica set size, then its
     * maximum replica set is returned.  The returned nodes may be
     * used for replicating data since they are precisely the nodes
     * which become roots for the key when the local node fails.
     *
     * @param key      the key
     * @param max_rank the maximal number of nodehandles returned
     * @return the replica set
     */
    public NodeHandleSet replicaSet(Id id, int maxRank) {
        LeafSet leafset = getLeafSet();
        if (maxRank > leafset.maxSize() / 2 + 1) {
            throw new IllegalArgumentException("maximum replicaSet size for this configuration exceeded; asked for " + maxRank + " but max is " + (leafset.maxSize() / 2 + 1));
        }
        if (maxRank > leafset.size()) {
            logger.debug("trying to get a replica set of size " + maxRank + " but only " + leafset.size() + " nodes in leafset");
        }

        return leafset.replicaSet((org.urajio.freshpastry.rice.pastry.Id) id, maxRank);
    }

    /**
     * This methods returns an ordered set of nodehandles on which replicas of an object with
     * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
     * This call also allows the application to provide a remote "center" node, as well as
     * other nodes in the vicinity.
     *
     * @param id      The object's id.
     * @param maxRank The number of desired replicas.
     * @param handle  The root handle of the remove set
     * @param set     The set of other nodes around the root handle
     */
    public NodeHandleSet replicaSet(Id id, int maxRank, NodeHandle root, NodeHandleSet set) {
        LeafSet leaf = new LeafSet((org.urajio.freshpastry.rice.pastry.NodeHandle) root, getLeafSet().maxSize(), false);
        for (int i = 0; i < set.size(); i++)
            leaf.put((org.urajio.freshpastry.rice.pastry.NodeHandle) set.getHandle(i));

        return leaf.replicaSet((org.urajio.freshpastry.rice.pastry.Id) id, maxRank);
    }

    /**
     * This method provides information about ranges of keys for which
     * the node n is currently a r-root. The operations returns null
     * if the range could not be determined. It is an error to query
     * the range of a node not present in the neighbor set as returned
     * by the update upcall or the neighborSet call.
     * <p>
     * Some implementations may have multiple, disjoint ranges of keys
     * for which a given node is responsible (Pastry has two). The
     * parameter key allows the caller to specify which range should
     * be returned.  If the node referenced by n is the r-root for
     * key, then the resulting range includes key. Otherwise, the
     * result is the nearest range clockwise from key for which n is
     * responsible.
     *
     * @param n          nodeHandle of the node whose range is being queried
     * @param r          the rank
     * @param key        the key
     * @param cumulative if true, returns ranges for which n is an i-root for 0<i<=r
     * @return the range of keys, or null if range could not be determined for the given node and rank
     */
    public IdRange range(NodeHandle n, int r, Id key, boolean cumulative) {
        org.urajio.freshpastry.rice.pastry.Id pKey = (org.urajio.freshpastry.rice.pastry.Id) key;

        if (cumulative)
            return getLeafSet().range((org.urajio.freshpastry.rice.pastry.NodeHandle) n, r);

        org.urajio.freshpastry.rice.pastry.IdRange ccw = getLeafSet().range((org.urajio.freshpastry.rice.pastry.NodeHandle) n, r, false);
        org.urajio.freshpastry.rice.pastry.IdRange cw = getLeafSet().range((org.urajio.freshpastry.rice.pastry.NodeHandle) n, r, true);

        if (cw == null || ccw.contains(pKey) || pKey.isBetween(cw.getCW(), ccw.getCCW())) return ccw;
        else return cw;
    }

    /**
     * This method provides information about ranges of keys for which
     * the node n is currently a r-root. The operations returns null
     * if the range could not be determined. It is an error to query
     * the range of a node not present in the neighbor set as returned
     * by the update upcall or the neighborSet call.
     * <p>
     * Some implementations may have multiple, disjoint ranges of keys
     * for which a given node is responsible (Pastry has two). The
     * parameter key allows the caller to specify which range should
     * be returned.  If the node referenced by n is the r-root for
     * key, then the resulting range includes key. Otherwise, the
     * result is the nearest range clockwise from key for which n is
     * responsible.
     *
     * @param n   nodeHandle of the node whose range is being queried
     * @param r   the rank
     * @param key the key
     * @return the range of keys, or null if range could not be determined for the given node and rank
     */
    public IdRange range(NodeHandle n, int r, Id key) {
        return range(n, r, key, false);
    }

    /**
     * Returns a handle to the local node below this endpoint.
     *
     * @return A NodeHandle referring to the local node.
     */
    public NodeHandle getLocalNodeHandle() {
        return thePastryNode.getLocalHandle();
    }

    // Upcall to Application support

    public final void messageForAppl(org.urajio.freshpastry.rice.pastry.messaging.Message msg) {
        logger.debug("[" + thePastryNode + "] deliver " + msg + " from " + msg.getSenderId());

        if (msg instanceof PastryEndpointMessage) {
            // null for now, when RouteMessage stuff is completed, then it will be different!
            application.deliver(null, ((PastryEndpointMessage) msg).getMessage());
        } else {
            logger.warn("Received unknown message " + msg + " - dropping on floor");
        }
    }

    public final boolean enrouteMessage(org.urajio.freshpastry.rice.pastry.messaging.Message msg, org.urajio.freshpastry.rice.pastry.Id key, org.urajio.freshpastry.rice.pastry.NodeHandle nextHop, SendOptions opt) {
        throw new RuntimeException("Should not be called, should only be handled by PastryEndpoint.receiveMessage()");
    }

    public void leafSetChange(org.urajio.freshpastry.rice.pastry.NodeHandle nh, boolean wasAdded) {
        application.update(nh, wasAdded);
    }

    // PastryAppl support

    /**
     * Called by pastry to deliver a message to this client.  Not to be overridden.
     *
     * @param msg the message that is arriving.
     */
    public void receiveMessage(org.urajio.freshpastry.rice.pastry.messaging.Message msg) {
        logger.debug("[" + thePastryNode + "] recv " + msg);

        if (msg instanceof org.urajio.freshpastry.rice.pastry.routing.RouteMessage) {
            try {
                org.urajio.freshpastry.rice.pastry.routing.RouteMessage rm = (org.urajio.freshpastry.rice.pastry.routing.RouteMessage) msg;

                // if the message has a destinationHandle, it should be for me, and it should always be delivered
                NodeHandle destinationHandle = rm.getDestinationHandle();
                if (deliverWhenNotReady() ||
                        thePastryNode.isReady() ||
                        rm.getPrevNode() == thePastryNode.getLocalHandle() ||
                        (destinationHandle != null && destinationHandle == thePastryNode.getLocalHandle())) {
                    // continue to receiveMessage()
                } else {
                    logger.info("Dropping " + msg + " because node is not ready.");
                    // enable this if you want to forward RouteMessages when not ready, without calling the "forward()" method on the PastryAppl that sent the message
//        rm.routeMessage(this.localNode.getLocalHandle());

//      undeliveredMessages.add(msg);
                    return;
                }

                // call application
                logger.debug("[" + thePastryNode + "] forward " + msg);
                if (application.forward(rm)) {
                    if (rm.getNextHop() != null) {
                        org.urajio.freshpastry.rice.pastry.NodeHandle nextHop = rm.getNextHop();

                        // if the message is for the local node, deliver it here
                        if (getNodeId().equals(nextHop.getNodeId())) {
                            PastryEndpointMessage pMsg = (PastryEndpointMessage) rm.unwrap(deserializer);
                            logger.debug("[" + thePastryNode + "] deliver " + pMsg + " from " + pMsg.getSenderId());
                            application.deliver(rm.getTarget(), pMsg.getMessage());
                            rm.sendSuccess(thePastryNode.getLocalHandle());
                        } else {
                            // route the message
                            // if getDestHandle() == me, rm destHandle()
                            // this message was directed just to me, but now we've decided to forward it, so,
                            // make it generally routable now
                            if (rm.getDestinationHandle() == thePastryNode.getLocalHandle()) {
                                logger.debug("Warning, removing destNodeHandle: " + rm.getDestinationHandle() + " from " + rm);
                                rm.setDestinationHandle(null);
                            }
                            thePastryNode.getRouter().route(rm);
                        }
                    }
                } else {
                    // forward consumed the message
                    rm.sendSuccess(thePastryNode.getLocalHandle());
                }
            } catch (IOException ioe) {
                logger.error(this.toString(), ioe);
            }
        } else {

            // if the message is not a RouteMessage, then it is for the local node and
            // was sent with a PastryAppl.routeMsgDirect(); we deliver it for backward compatibility
            messageForAppl(msg);
        }
    }

    /**
     * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
     * as encryption, erasure encoding, or bloom filter creation should never be done in the context
     * of the underlying node's thread, and should only be done via this method.
     *
     * @param task    The task to run on the processing thread
     * @param command The command to return the result to once it's done
     */
    @SuppressWarnings("unchecked")
    public void process(Executable task, Continuation command) {
        thePastryNode.process(task, command);
    }

    /**
     * Returns a unique instance name of this endpoint, sort of a mailbox name for this
     * application.
     *
     * @return The unique instance name of this application
     */
    public String getInstance() {
        return instance;
    }

    /* (non-Javadoc)
     * @see rice.p2p.commonapi.Endpoint#getEnvironment()
     */
    public Environment getEnvironment() {
        return thePastryNode.getEnvironment();
    }

//  /**
//   * Translate to a pastry.NodeHandle, otherwise, this is a passthrough function.
//   */
//  public void connect(NodeHandle handle, AppSocketReceiver receiver, int timeout) {
//    connect((rice.pastry.NodeHandle)handle, receiver, timeout);
//  }

    public String toString() {
        return "PastryEndpoint " + application + " " + instance + " " + getAddress();
    }

    public MessageDeserializer getDeserializer() {
        return appDeserializer;
    }

    public void setDeserializer(MessageDeserializer md) {
        appDeserializer = md;
    }

    public Id readId(InputBuffer buf, short type) throws IOException {
        if (type != org.urajio.freshpastry.rice.pastry.Id.TYPE)
            throw new IllegalArgumentException("Invalid type:" + type);
        return org.urajio.freshpastry.rice.pastry.Id.build(buf);
    }

    public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
        return thePastryNode.readNodeHandle(buf);
    }

    public IdRange readIdRange(InputBuffer buf) throws IOException {
        return new org.urajio.freshpastry.rice.pastry.IdRange(buf);
    }

    public NodeHandle coalesce(NodeHandle newHandle) {
        return thePastryNode.coalesce((org.urajio.freshpastry.rice.pastry.NodeHandle) newHandle);
    }

    public NodeHandleSet readNodeHandleSet(InputBuffer buf, short type) throws IOException {
        switch (type) {
            case NodeSet.TYPE:
                return new NodeSet(buf, thePastryNode);
            case RouteSet.TYPE:
                return new RouteSet(buf, thePastryNode, thePastryNode);
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    public List<NodeHandle> networkNeighbors(int num) {
        HashSet<NodeHandle> handles = new HashSet<>();
        List<org.urajio.freshpastry.rice.pastry.NodeHandle> l = thePastryNode.getRoutingTable().asList();
        Iterator<org.urajio.freshpastry.rice.pastry.NodeHandle> i = l.iterator();
        while (i.hasNext()) {
            handles.add(i.next());
        }
        l = thePastryNode.getLeafSet().asList();
        i = l.iterator();
        while (i.hasNext()) {
            handles.add(i.next());
        }

        NodeHandle[] array = handles.toArray(new NodeHandle[0]);

        Arrays.sort(array, new Comparator<NodeHandle>() {
            public int compare(NodeHandle a, NodeHandle b) {
                return thePastryNode.proximity((org.urajio.freshpastry.rice.pastry.NodeHandle) a) - thePastryNode.proximity((org.urajio.freshpastry.rice.pastry.NodeHandle) b);
            }
        });


        if (array.length <= num) return Arrays.asList(array);

        NodeHandle[] ret = new NodeHandle[num];
        System.arraycopy(array, 0, ret, 0, num);

        return Arrays.asList(ret);
    }

    @Override
    public void destroy() {
        if (application != null) {
            if (application instanceof Destructable) {
                ((Destructable) application).destroy();
            }
        }
        super.destroy();
    }

    public int proximity(NodeHandle nh) {
        return thePastryNode.proximity((org.urajio.freshpastry.rice.pastry.NodeHandle) nh);
    }

    public boolean isAlive(NodeHandle nh) {
        return thePastryNode.isAlive((org.urajio.freshpastry.rice.pastry.NodeHandle) nh);
    }

    public int getAppId() {
        return getAddress();
    }

    public void setConsistentRouting(boolean val) {
        consistentRouting = val;
    }

    @Override
    public boolean deliverWhenNotReady() {
        return !consistentRouting;
    }

    public boolean routingConsistentFor(Id id) {
        if (!thePastryNode.isReady()) return false;
        NodeHandleSet set = replicaSet(id, 1);
        if (set.size() == 0) return false;
        return set.getHandle(0).equals(thePastryNode.getLocalHandle());
    }

    public void setSendOptions(Map<String, Object> options) {
        this.options = options;
    }

    class PEDeserializer implements MessageDeserializer {
        public Message deserialize(InputBuffer buf, short type, int priority,
                                   NodeHandle sender) throws IOException {
//      if (type == PastryEndpointMessage.TYPE)
            try {
                return new PastryEndpointMessage(getAddress(), buf, appDeserializer, type, priority, (org.urajio.freshpastry.rice.pastry.NodeHandle) sender);
            } catch (IllegalArgumentException iae) {
                logger.info("Unable to deserialize message of type " + type + " " + PastryEndpoint.this + " " + appDeserializer);
                throw iae;
            }
//    return null;
//      throw new IllegalArgumentException("Unknown type "+type+" (priority:"+priority+" sender:"+sender+" appDes:"+appDeserializer+")");
        }
    }
}
