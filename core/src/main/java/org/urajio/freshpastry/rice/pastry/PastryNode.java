package org.urajio.freshpastry.rice.pastry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.Executable;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.appsocket.AppSocketReceiver;
import org.urajio.freshpastry.rice.p2p.commonapi.exception.AppNotRegisteredException;
import org.urajio.freshpastry.rice.p2p.commonapi.exception.AppSocketException;
import org.urajio.freshpastry.rice.p2p.commonapi.exception.NoReceiverAvailableException;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.join.JoinProtocol;
import org.urajio.freshpastry.rice.pastry.leafset.InitiateLeafSetMaintenance;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSetProtocol;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.messaging.MessageDispatch;
import org.urajio.freshpastry.rice.pastry.messaging.PJavaSerializedMessage;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;
import org.urajio.freshpastry.rice.pastry.routing.InitiateRouteSetMaintenance;
import org.urajio.freshpastry.rice.pastry.routing.RouteSetProtocol;
import org.urajio.freshpastry.rice.pastry.routing.Router;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceiptImpl;
import org.urajio.freshpastry.rice.pastry.transport.SocketAdapter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * A Pastry node is single entity in the pastry network.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class PastryNode extends Observable implements
        org.urajio.freshpastry.rice.p2p.commonapi.Node, Destructable, NodeHandleFactory,
        LivenessProvider<NodeHandle>, ProximityProvider<NodeHandle>,
        ProximityListener<NodeHandle>,
        TransportLayerCallback<NodeHandle, RawMessage>,
        LivenessListener<NodeHandle> {
    /**
     * Used by AppSockets
     */
    public static final byte CONNECTION_UNKNOWN_ERROR = -1;
    public static final byte CONNECTION_UNKNOWN = -100;
    public static final byte CONNECTION_OK = 0;
    public static final byte CONNECTION_NO_APP = 1;
    public static final byte CONNECTION_NO_ACCEPTOR = 2;
    private final static Logger logger = LoggerFactory.getLogger(PastryNode.class);
    final Collection<LivenessListener<NodeHandle>> livenessListeners = new ArrayList<>();
    /******************* network listeners *********************/
    // the list of network listeners
    private final ArrayList<NetworkListener> networkListeners = new ArrayList<>();
    protected Id myNodeId;
    protected LeafSet leafSet;

    protected RoutingTable routeSet;

    protected NodeHandle localhandle;

    protected Vector apps;
    protected boolean joinFailed = false;
    protected boolean isDestroyed = false;
    protected Router router;
    /**
     * Used to deserialize NodeHandles
     */
    protected NodeHandleFactory handleFactory;
    /**
     * Call initiateJoin on this class.
     */
    protected JoinProtocol joiner;
    /**
     * Call boot on this class.
     */
    protected Bootstrapper bootstrapper;
    /**
     * The top level transport layer.
     */
    protected TransportLayer<NodeHandle, RawMessage> tl;
    protected ProximityProvider<NodeHandle> proxProvider;
    protected JoinFailedException joinFailedReason;
    // Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
    // 0 means never.
    protected int leafSetMaintFreq, routeSetMaintFreq;
    protected ScheduledMessage leafSetRoutineMaintenance = null;
    protected ScheduledMessage routeSetRoutineMaintenance = null;
    protected LivenessProvider<NodeHandle> livenessProvider;
    ReadyStrategy readyStrategy;
    ReadyStrategy defaultReadyStrategy = null;
    HashSet<Destructable> destructables = new HashSet<>();
    Map<String, Object> vars = new HashMap<>();
    NodeHandleFetcher nodeHandleFetcher;
    private Environment myEnvironment;
    private MessageDispatch myMessageDispatch;
    /**
     * This variable makes it so notifyReady() is only called on the apps once.
     * Deprecating
     */
    private boolean neverBeenReady = true;

    /**
     * Constructor, with NodeId. Need to set the node's ID before this node is
     * inserted as localHandle.localNode.
     */
    public PastryNode(Id id, Environment e) {
        myEnvironment = e;
        myNodeId = id;

        readyStrategy = getDefaultReadyStrategy();

        apps = new Vector();
        e.addDestructable(this);
    }

    public void boot(Object o) {
        if (o == null) {
            getBootstrapper().boot(Collections.EMPTY_LIST);
        } else {
            if (o instanceof Collection) {
                boot((Collection) o);
            } else {
                getBootstrapper().boot(Collections.singleton(o));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void boot(Collection o2) {
        ArrayList o = new ArrayList(o2);
        while (o.remove(null)) ; // remove all null
        getBootstrapper().boot(o);
    }

    /**
     * Simple Ready Strategy
     */
    public ReadyStrategy getDefaultReadyStrategy() {
        if (defaultReadyStrategy != null) return defaultReadyStrategy;
        defaultReadyStrategy = new ReadyStrategy() {
            private boolean ready = false;

            public boolean isReady() {
                return ready;
            }

            public void setReady(boolean r) {
                if (r != ready) {
                    synchronized (PastryNode.this) {
                        ready = r;
                    }
                    notifyReadyObservers();
                }
            }

            public void start() {
                setReady(true);
            }

            public void stop() {
                // don't need to do any initialization
            }
        };
        return defaultReadyStrategy;
    }

    public void setReadyStrategy(ReadyStrategy rs) {
        this.readyStrategy = rs;
    }

    /**
     * Combined accessor method for various members of PastryNode. These are
     * generated by node factories, and assigned here.
     * <p>
     * Other elements specific to the wire protocol are assigned via methods
     * set{RMI,Direct}Elements in the respective derived classes.
     *
     * @param lh Node handle corresponding to this node.
     * @param md Message dispatcher.
     * @param ls Leaf set.
     * @param rt Routing table.
     */
    public void setElements(NodeHandle lh, MessageDispatch md, LeafSet ls, RoutingTable rt, Router router) {
        localhandle = lh;
        setMessageDispatch(md);
        leafSet = ls;
        routeSet = rt;
        this.router = router;
    }

    public void setJoinProtocols(Bootstrapper boot, JoinProtocol joinP, LeafSetProtocol leafsetP, RouteSetProtocol routeP) {
        this.bootstrapper = boot;
        this.joiner = joinP;
    }

    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle getLocalNodeHandle() {
        return localhandle;
    }

    public Environment getEnvironment() {
        return myEnvironment;
    }

    public NodeHandle getLocalHandle() {
        return localhandle;
    }

    public Id getNodeId() {
        return myNodeId;
    }

    public boolean isReady() {
        return readyStrategy.isReady();
    }

    public void setReady(boolean ready) {
        readyStrategy.setReady(ready);
    }

    /**
     * FOR TESTING ONLY - DO NOT USE!
     */
    public MessageDispatch getMessageDispatch() {
        return myMessageDispatch;
    }

    public void setMessageDispatch(MessageDispatch md) {
        myMessageDispatch = md;
        addDestructable(myMessageDispatch);
    }

    public Destructable addDestructable(Destructable d) {
        destructables.add(d);
        return d;
    }

    public boolean removeDestructable(Destructable d) {
        return destructables.remove(d);
    }

    /**
     * Overridden by derived classes, and invoked when the node has joined
     * successfully. This should probably be abstract, but maybe in a later
     * version.
     *
     * @param state true when the node is ready, false when not
     */
    public void nodeIsReady(boolean state) {
    }

    public void setReady() {
        setReady(true);
    }

    public NodeHandle coalesce(NodeHandle newHandle) {
        logger.debug("coalesce(" + newHandle + ")");
        return handleFactory.coalesce(newHandle);
    }

    public void notifyReadyObservers() {

        // It is possible to have the setReady() invoked more than once if the
        // message denoting the termination of join protocol is duplicated.
        boolean ready = readyStrategy.isReady();
        //      if (r == false)
        logger.info("PastryNode.notifyReadyObservers(" + ready + ")");

        if (ready) {
            nodeIsReady(true);

            setChanged();
            notifyObservers(Boolean.TRUE);

            if (neverBeenReady) {
                // notify applications
                // we iterate over private copy to allow addition of new apps in the
                // context of notifyReady()
                Vector<PastryAppl> tmpApps = new Vector(apps);
                for (PastryAppl tmpApp : tmpApps) tmpApp.notifyReady();
                neverBeenReady = false;
            }

            // signal any apps that might be waiting for the node to get ready
            synchronized (this) {
                // NN: not a problem, because we already changed the state in the calling method
                notifyAll();
            }
        } else {
            nodeIsReady(false);
            setChanged();
            notifyObservers(Boolean.FALSE);
        }
    }

    /**
     * Called by the layered Pastry application to check if the local pastry node
     * is the one that is currently closest to the object key id.
     *
     * @param key the object key id
     * @return true if the local node is currently the closest to the key.
     */
    public boolean isClosest(Id key) {

        return leafSet.mostSimilar(key) == 0;
    }

    public LeafSet getLeafSet() {
        return leafSet;
    }

//  public synchronized void receiveMessage(RawMessageDelivery delivery) {
//    myMessageDispatch.dispatchMessage(delivery); 
//  }

    public RoutingTable getRoutingTable() {
        return routeSet;
    }

    /**
     * Add a leaf set observer to the Pastry node.
     *
     * @param o the observer.
     * @deprecated use addLeafSetListener
     */
    public void addLeafSetObserver(Observer o) {
        leafSet.addObserver(o);
    }

    /**
     * Delete a leaf set observer from the Pastry node.
     *
     * @param o the observer.
     * @deprecated use deleteLeafSetListener
     */
    public void deleteLeafSetObserver(Observer o) {
        leafSet.deleteObserver(o);
    }

    // Common API Support

    public void addLeafSetListener(NodeSetListener listener) {
        leafSet.addNodeSetListener(listener);
    }

    public void deleteLeafSetListener(NodeSetListener listener) {
        leafSet.deleteNodeSetListener(listener);
    }

    /**
     * Add a route set observer to the Pastry node.
     *
     * @param o the observer.
     * @deprecated use addRouteSetListener
     */
    public void addRouteSetObserver(Observer o) {
        routeSet.addObserver(o);
    }

    /**
     * Delete a route set observer from the Pastry node.
     *
     * @param o the observer.
     * @deprecated use deleteRouteSetListener
     */
    public void deleteRouteSetObserver(Observer o) {
        routeSet.deleteObserver(o);
    }

    public void addRouteSetListener(NodeSetListener listener) {
        routeSet.addNodeSetListener(listener);
    }

    public void removeRouteSetListener(NodeSetListener listener) {
        routeSet.removeNodeSetListener(listener);
    }

    /**
     * message receiver interface. synchronized so that the external message
     * processing thread and the leafset/route maintenance thread won't interfere
     * with application messages.
     */
    public synchronized void receiveMessage(Message msg) {
        if (isDestroyed) return;
        logger.debug("receiveMessage(" + msg + ")");
        myMessageDispatch.dispatchMessage(msg);
    }

    /**
     * Registers a message receiver with this Pastry node.
     *
     * @param address  the address that the receiver will be at.
     * @param receiver the message receiver.
     */
    public void registerReceiver(int address,
                                 PastryAppl receiver) {
        logger.debug("registerReceiver(" + address + "," + receiver + "):" + receiver.getDeserializer());
        myMessageDispatch.registerReceiver(address, receiver);
    }

    /**
     * Registers an application with this pastry node.
     *
     * @param app the application
     */

    public void registerApp(PastryAppl app) {
        if (isReady())
            app.notifyReady();
        apps.add(app);
    }

    public String toString() {
        return "PastryNode" + localhandle;
//    return "Pastry node " + myNodeId.toString();
    }

    /**
     * This returns a VirtualizedNode specific to the given application and
     * instance name to the application, which the application can then use in
     * order to send an receive messages.
     *
     * @param application The Application
     * @param instance    An identifier for a given instance
     * @return The endpoint specific to this applicationk, which can be used for
     * message sending/receiving.
     * @deprecated use buildEndpoint() endpoint.register()
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.Endpoint registerApplication(
            org.urajio.freshpastry.rice.p2p.commonapi.Application application, String instance) {
        return new org.urajio.freshpastry.rice.pastry.commonapi.PastryEndpoint(this, application, instance, true);
    }

    public org.urajio.freshpastry.rice.p2p.commonapi.Endpoint buildEndpoint(
            org.urajio.freshpastry.rice.p2p.commonapi.Application application, String instance) {
        return new org.urajio.freshpastry.rice.pastry.commonapi.PastryEndpoint(this, application, instance, false);
    }

    /**
     * Returns the Id of this node
     *
     * @return This node's Id
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.Id getId() {
        return getNodeId();
    }

    /**
     * Returns a factory for Ids specific to this node's protocol.
     *
     * @return A factory for creating Ids.
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.IdFactory getIdFactory() {
        return new org.urajio.freshpastry.rice.pastry.commonapi.PastryIdFactory(getEnvironment());
    }

    // from TLPastryNode

    // TODO: this all needs to go!

    /**
     * Schedules a job for processing on the dedicated processing thread, should
     * one exist. CPU intensive jobs, such as encryption, erasure encoding, or
     * bloom filter creation should never be done in the context of the underlying
     * node's thread, and should only be done via this method.
     *
     * @param task    The task to run on the processing thread
     * @param command The command to return the result to once it's done
     */
    @SuppressWarnings("unchecked")
    public void process(Executable task, Continuation command) {
        try {
            myEnvironment.getProcessor().process(task,
                    command,
                    myEnvironment.getSelectorManager(),
                    myEnvironment.getTimeSource());

//      command.receiveResult(task.execute());
        } catch (final Exception e) {
            command.receiveException(e);
        }
    }

    /**
     * Method which kills a PastryNode.  Note, this doesn't implicitly kill the environment.
     * <p>
     * Make sure to call super.destroy() !!!
     */
    public void destroy() {
        if (isDestroyed) return;
        logger.info("Destroying " + this);
        isDestroyed = true;
        for (Destructable d : destructables) {
            logger.info("Destroying " + d);
            d.destroy();
        }
        getEnvironment().removeDestructable(this);
        if (getEnvironment().getSelectorManager().isSelectorThread()) {
            if (tl != null) tl.destroy();
        } else {
            getEnvironment().getSelectorManager().invoke(new Runnable() {
                public void run() {
                    if (tl != null) tl.destroy();
                }
            });
        }
    }

    /**
     * Called by PastryAppl to ask the transport layer to open a Socket to its counterpart on another node.
     *
     * @param i                 handle
     * @param deliverSocketToMe receiver
     * @param appl
     */
    @SuppressWarnings("unchecked")
    public SocketRequestHandle connect(final NodeHandle i, final AppSocketReceiver deliverSocketToMe,
                                       final PastryAppl appl, int timeout) {

//    final SocketNodeHandle i = (SocketNodeHandle)i2;

        final SocketRequestHandleImpl<NodeHandle> handle = new SocketRequestHandleImpl<>(i, null);

        Runnable r = new Runnable() {
            public void run() {

                // use the proper application address
                final ByteBuffer b = ByteBuffer.allocate(4);
                b.asIntBuffer().put(appl.getAddress());
                b.clear();


                handle.setSubCancellable(tl.openSocket(i,
                        new SocketCallback<NodeHandle>() {
                            public void receiveResult(SocketRequestHandle<NodeHandle> c, P2PSocket<NodeHandle> result) {

                                if (c != handle.getSubCancellable()) {
                                    throw new RuntimeException("c != handle.getSubCancellable() (indicates a bug in the code) c:" + c + " sub:" + handle.getSubCancellable());
                                }
                                logger.debug("openSocket(" + i + "):receiveResult(" + result + ")");
                                result.register(false, true, new P2PSocketReceiver<NodeHandle>() {
                                    public void receiveSelectResult(P2PSocket<NodeHandle> socket,
                                                                    boolean canRead, boolean canWrite) throws IOException {
                                        if (canRead || !canWrite) {
                                            throw new IOException("Expected to write! " + canRead + "," + canWrite);
                                        }

                                        // write the appId
                                        if (socket.write(b) == -1) {
                                            deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                                            return;
                                        }

                                        // keep working or pass up the new socket
                                        if (b.hasRemaining()) {
                                            // keep writing
                                            socket.register(false, true, this);
                                        } else {
                                            // read the response
                                            final ByteBuffer answer = ByteBuffer.allocate(1);
                                            socket.register(true, false, new P2PSocketReceiver<NodeHandle>() {

                                                public void receiveSelectResult(P2PSocket<NodeHandle> socket, boolean canRead, boolean canWrite) throws IOException {

                                                    if (socket.read(answer) == -1) {
                                                        deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                                                        return;
                                                    }

                                                    if (answer.hasRemaining()) {
                                                        socket.register(true, false, this);
                                                    } else {
                                                        answer.clear();

                                                        byte connectResult = answer.get();
                                                        //System.out.println(this+"Read "+connectResult);
                                                        switch (connectResult) {
                                                            case CONNECTION_OK:
                                                                // on connector side
                                                                deliverSocketToMe.receiveSocket(new SocketAdapter(socket, getEnvironment()));
                                                                return;
                                                            case CONNECTION_NO_APP:
                                                                deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new AppNotRegisteredException(appl.getAddress()));
                                                                return;
                                                            case CONNECTION_NO_ACCEPTOR:
                                                                deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new NoReceiverAvailableException());
                                                                return;
                                                            default:
                                                                deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new AppSocketException("Unknown error " + connectResult));
                                                        }
                                                    }
                                                }

                                                public void receiveException(P2PSocket<NodeHandle> socket, Exception ioe) {
                                                    deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), ioe);
                                                }
                                            });
                                        }
                                    }

                                    public void receiveException(P2PSocket<NodeHandle> socket,
                                                                 Exception e) {
                                        deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), e);
                                    }
                                });
                            }

                            public void receiveException(SocketRequestHandle<NodeHandle> s, Exception ex) {
                                // TODO: return something with a proper toString()
                                deliverSocketToMe.receiveException(null, ex);
                            }
                        },
                        null));
            }
        };
        if (myEnvironment.getSelectorManager().isSelectorThread()) {
            r.run();
        } else {
            myEnvironment.getSelectorManager().invoke(r);
        }


        return handle;
    }

    public void joinFailed(JoinFailedException cje) {
        logger.warn("joinFailed(" + cje + ")");
        joinFailedReason = cje;
        synchronized (this) {
            joinFailed = true;
            this.notifyAll();
        }
        setChanged();
        this.notifyObservers(cje);
    }

    /**
     * Returns true if there was a fatal error Joining
     *
     * @return
     */
    public boolean joinFailed() {
        return joinFailed;
    }

    public JoinFailedException joinFailedReason() {
        return joinFailedReason;
    }

    public Router getRouter() {
        return router;
    }

    public String printRouteState() {
        String ret = leafSet.toString() + "\n";
        ret += routeSet.toString();
        return ret;
    }

    public void setSocketElements(int lsmf, int rsmf,
                                  TransportLayer<NodeHandle, RawMessage> tl,
                                  LivenessProvider<NodeHandle> livenessProvider,
                                  ProximityProvider<NodeHandle> proxProvider,
                                  NodeHandleFactory handleFactory) {
        this.leafSetMaintFreq = lsmf;
        this.routeSetMaintFreq = rsmf;
        this.handleFactory = handleFactory;
        this.proxProvider = proxProvider;
        proxProvider.addProximityListener(this);

        this.tl = tl;
        this.livenessProvider = livenessProvider;
        tl.setCallback(this);
        livenessProvider.addLivenessListener(this);
    }

    public Map<String, Object> getVars() {
        return vars;
    }

    public void incomingSocket(P2PSocket<NodeHandle> s) {

        // read the appId
        final ByteBuffer appIdBuffer = ByteBuffer.allocate(4);

        s.register(true, false, new P2PSocketReceiver<NodeHandle>() {

            public void receiveSelectResult(
                    P2PSocket<NodeHandle> socket,
                    boolean canRead, boolean canWrite) throws IOException {
                // read the appId
                if (socket.read(appIdBuffer) == -1) {
                    logger.warn("AppId Socket from " + socket + " closed unexpectedly.");
                    return;
                }

                if (appIdBuffer.hasRemaining()) {
                    // read the rest;
                    socket.register(true, false, this);
                } else {
                    appIdBuffer.clear();
                    final int appId = appIdBuffer.asIntBuffer().get();

                    // we need to write the result, and there is a timing issure on the appl, so we need to first request to write, then do everything
                    // the alternative approach is to return a dummy socket (or a wrapper) and cache any registration request until we write the response
                    socket.register(false, true, new P2PSocketReceiver<NodeHandle>() {

                        public void receiveSelectResult(P2PSocket<NodeHandle> socket,
                                                        boolean canRead, boolean canWrite) throws IOException {

                            PastryAppl acceptorAppl = getMessageDispatch().getDestinationByAddress(appId);

                            ByteBuffer toWrite = ByteBuffer.allocate(1);
                            boolean success = false;

                            if (acceptorAppl == null) {
                                logger.warn("Sending error to connecter " + socket + " " + new AppNotRegisteredException(appId));
                                toWrite.put(CONNECTION_NO_APP);
                                toWrite.clear();
                                socket.write(toWrite);
                                socket.close();
                            } else {
                                synchronized (acceptorAppl) {
                                    // try to register with the application
                                    if (acceptorAppl.canReceiveSocket()) {
                                        toWrite.put(CONNECTION_OK);
                                        toWrite.clear();
                                        success = true;
                                    } else {
                                        logger.warn("Sending error to connecter " + socket + " " + new NoReceiverAvailableException());
                                        toWrite.put(CONNECTION_NO_ACCEPTOR);
                                        toWrite.clear();
                                    }

                                    socket.write(toWrite);
                                    if (toWrite.hasRemaining()) {
                                        // this sucks, because the snychronization with the app-receiver becomes all wrong, this shouldn't normally happen
                                        logger.warn("couldn't write 1 bite!!! " + toWrite);
                                        socket.close();
                                        return;
                                    }

                                    if (success) {
                                        acceptorAppl.finishReceiveSocket(new SocketAdapter(socket, getEnvironment()));
                                    }
                                } // sync
                            } // if (acceptorAppl!=null)
                        } // rSR()

                        public void receiveException(P2PSocket<NodeHandle> socket, Exception ioe) {
                            logger.warn("incomingSocket(" + socket + ")", ioe);
                        }
                    });
                }
            }

            public void receiveException(
                    P2PSocket<NodeHandle> socket,
                    Exception ioe) {
                logger.warn("incomingSocket(" + socket + ")", ioe);
            }

        });
    }

    protected void acceptAppSocket(int appId) throws AppSocketException {
        PastryAppl acceptorAppl = getMessageDispatch().getDestinationByAddress(appId);
        if (acceptorAppl == null) throw new AppNotRegisteredException(appId);
        if (!acceptorAppl.canReceiveSocket()) throw new NoReceiverAvailableException();
    }

    /**
     * The proximity of the node handle.
     *
     * @param nh
     * @return
     */
    public int proximity(NodeHandle nh) {
        return proximity(nh, null);
    }

    public int proximity(NodeHandle nh, Map<String, Object> options) {
        return proxProvider.proximity(nh, options);
    }

    /**
     * Schedule the specified message to be sent to the local node after a
     * specified delay. Useful to provide timeouts.
     *
     * @param msg   a message that will be delivered to the local node after the
     *              specified delay
     * @param delay time in milliseconds before message is to be delivered
     * @return the scheduled event object; can be used to cancel the message
     */
    public ScheduledMessage scheduleMsg(Message msg, long delay) {
        ScheduledMessage sm = new ScheduledMessage(this, msg);
        getEnvironment().getSelectorManager().getTimer().schedule(sm, delay);
        return sm;
    }

    /**
     * Schedule the specified message for repeated fixed-delay delivery to the
     * local node, beginning after the specified delay. Subsequent executions take
     * place at approximately regular intervals separated by the specified period.
     * Useful to initiate periodic tasks.
     *
     * @param msg    a message that will be delivered to the local node after the
     *               specified delay
     * @param delay  time in milliseconds before message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message
     */
    public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
        ScheduledMessage sm = new ScheduledMessage(this, msg);
        getEnvironment().getSelectorManager().getTimer().schedule(sm, delay, period);
        return sm;
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
    public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
        ScheduledMessage sm = new ScheduledMessage(this, msg);
        getEnvironment().getSelectorManager().getTimer().scheduleAtFixedRate(sm, delay, period);
        return sm;
    }

//  public void setElements(NodeHandle lh, MessageDispatch md, LeafSet ls, RoutingTable rt, Router router, Bootstrapper bootstrapper) {
//    super.setElements(lh, md, ls, rt, router);
//    this.bootstrapper = bootstrapper;
//  }

    /**
     * Deliver message to the NodeHandle.
     */
    public PMessageReceipt send(final NodeHandle handle,
                                final Message msg,
                                final PMessageNotification deliverAckToMe,
                                Map<String, Object> tempOptions) {

        // set up the priority field in the options
        if (tempOptions != null && tempOptions.containsKey(PriorityTransportLayer.OPTION_PRIORITY)) {
            // already has the priority;
        } else {
            if (tempOptions == null) {
                tempOptions = new HashMap<>();
            } else {
                tempOptions = new HashMap<>(tempOptions);
            }
            tempOptions.put(PriorityTransportLayer.OPTION_PRIORITY, msg.getPriority());
        }

        final Map<String, Object> options = tempOptions;

        if (handle.equals(localhandle)) {
            receiveMessage(msg);
            PMessageReceipt ret = new PMessageReceipt() {

                public boolean cancel() {
                    return false;
                }

                public NodeHandle getIdentifier() {
                    return localhandle;
                }

                public Map<String, Object> getOptions() {
                    return options;
                }

                public Message getMessage() {
                    return msg;
                }

                public String toString() {
                    return "TLPN$PMsgRecpt{" + msg + "," + localhandle + "}";
                }
            };
            if (deliverAckToMe != null) deliverAckToMe.sent(ret);
            return ret;
        }

        final PRawMessage rm;
        if (msg instanceof PRawMessage) {
            rm = (PRawMessage) msg;
        } else {
            rm = new PJavaSerializedMessage(msg);
        }

        final PMessageReceiptImpl ret = new PMessageReceiptImpl(msg, options);
        final MessageCallback<NodeHandle, RawMessage> callback;
        if (deliverAckToMe == null) {
            callback = null;
        } else {
            callback = new MessageCallback<NodeHandle, RawMessage>() {
                public void ack(MessageRequestHandle<NodeHandle, RawMessage> msg) {
                    if (ret.getInternal() == null) ret.setInternal(msg);
                    deliverAckToMe.sent(ret);
                }

                public void sendFailed(MessageRequestHandle<NodeHandle, RawMessage> msg, Exception reason) {
                    if (ret.getInternal() == null) ret.setInternal(msg);
                    deliverAckToMe.sendFailed(ret, reason);
                }
            };
        }
        if (getEnvironment().getSelectorManager().isSelectorThread()) {
            ret.setInternal(tl.sendMessage(handle, rm, callback, options));
        } else {
            getEnvironment().getSelectorManager().invoke(new Runnable() {
                public void run() {
                    ret.setInternal(tl.sendMessage(handle, rm, callback, options));
                }
            });
        }
        return ret;
    }

    public void messageReceived(NodeHandle i, RawMessage m, Map<String, Object> options) {
        if (m.getType() == 0 && (m instanceof PJavaSerializedMessage)) {
            receiveMessage(((PJavaSerializedMessage) m).getMessage());
        } else {
            receiveMessage((Message) m);
        }
    }

    public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
        return handleFactory.readNodeHandle(buf);
    }

    public Bootstrapper getBootstrapper() {
        return bootstrapper;
    }

    /**
     * Called after the node is initialized.
     *
     * @param bootstrap The node which this node should boot off of.
     */
    public void doneNode(Collection<NodeHandle> bootstrap) {
        logger.info("doneNode:" + bootstrap);
//    doneNode(bootstrap.toArray(new NodeHandle[1]));
//  }
//
//  public void doneNode(NodeHandle[] bootstrap) {
        logger.info("doneNode:" + bootstrap);
        if (routeSetMaintFreq > 0) {
            // schedule the routeset maintenance event
            routeSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(),
                    routeSetMaintFreq * 1000, routeSetMaintFreq * 1000);
            logger.info("Scheduling routeSetMaint for " + routeSetMaintFreq * 1000 + "," + routeSetMaintFreq * 1000);
        }
        if (leafSetMaintFreq > 0) {
            // schedule the leafset maintenance event
            leafSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(),
                    leafSetMaintFreq * 1000, leafSetMaintFreq * 1000);
            logger.info("Scheduling leafSetMaint for " + leafSetMaintFreq * 1000 + "," + leafSetMaintFreq * 1000);
        }

        joiner.initiateJoin(bootstrap);
//    initiateJoin(bootstrap);
    }

    public void livenessChanged(NodeHandle i, int val, Map<String, Object> options) {
        if (val == LIVENESS_ALIVE) {
            i.update(NodeHandle.DECLARED_LIVE);
        } else {
            if (val >= LIVENESS_DEAD) {
                i.update(NodeHandle.DECLARED_DEAD);
            }
        }

        notifyLivenessListeners(i, val, options);
    }

    public void addLivenessListener(LivenessListener<NodeHandle> name) {
        synchronized (livenessListeners) {
            livenessListeners.add(name);
        }
    }

    public boolean removeLivenessListener(LivenessListener<NodeHandle> name) {
        synchronized (livenessListeners) {
            return livenessListeners.remove(name);
        }
    }

    protected void notifyLivenessListeners(NodeHandle i, int val, Map<String, Object> options) {
        logger.debug("notifyLivenessListeners(" + i + "," + val + ")");
        ArrayList<LivenessListener<NodeHandle>> temp;
        synchronized (livenessListeners) {
            temp = new ArrayList<>(livenessListeners);
        }
        for (LivenessListener<NodeHandle> ll : temp) {
            ll.livenessChanged(i, val, options);
        }
    }

    public boolean checkLiveness(NodeHandle i, Map<String, Object> options) {
        return livenessProvider.checkLiveness(i, options);
    }

    public int getLiveness(NodeHandle i, Map<String, Object> options) {
        return livenessProvider.getLiveness(i, options);
    }

    public int getLiveness(NodeHandle i) {
        return livenessProvider.getLiveness(i, null);
    }

    public boolean isAlive(NodeHandle i) {
        return (livenessProvider.getLiveness(i, null) < LIVENESS_DEAD);
    }

    public void proximityChanged(NodeHandle handle, int val, Map<String, Object> options) {
//    SocketNodeHandle handle = ((SocketNodeHandle)i);
        handle.update(NodeHandle.PROXIMITY_CHANGED);
    }

    public LivenessProvider<NodeHandle> getLivenessProvider() {
        return livenessProvider;
    }

    public ProximityProvider<NodeHandle> getProxProvider() {
        return proxProvider;
    }

    public TransportLayer<NodeHandle, RawMessage> getTL() {
        return tl;
    }

    public void clearState(NodeHandle i) {
        livenessProvider.clearState(i);
    }

    public void addProximityListener(ProximityListener<NodeHandle> listener) {
        proxProvider.addProximityListener(listener);
    }

    public boolean removeProximityListener(ProximityListener<NodeHandle> listener) {
        return proxProvider.removeProximityListener(listener);
    }

    public NodeHandleFactory getHandleFactroy() {
        return handleFactory;
    }

    public void addNetworkListener(NetworkListener listener) {
        synchronized (networkListeners) {
            networkListeners.add(listener);
        }
    }

    public void removeNetworkListener(NetworkListener listener) {
        synchronized (networkListeners) {
            networkListeners.remove(listener);
        }
    }

    protected Iterable<NetworkListener> getNetworkListeners() {
        synchronized (networkListeners) {
            return new ArrayList<>(networkListeners);
        }
    }

    public void broadcastChannelClosed(InetSocketAddress addr) {
        for (NetworkListener listener : getNetworkListeners())
            listener.channelClosed(addr);
    }

    public void broadcastChannelOpened(InetSocketAddress addr, int reason) {
        for (NetworkListener listener : getNetworkListeners())
            listener.channelOpened(addr, reason);
    }

    public void broadcastSentListeners(int address, short msgType, InetSocketAddress dest, int size, int wireType) {
        for (NetworkListener listener : getNetworkListeners())
            listener.dataSent(address, msgType, dest, size, wireType);
    }

    public void broadcastReceivedListeners(int address, short msgType, InetSocketAddress from, int size, int wireType) {
        for (NetworkListener listener : getNetworkListeners())
            listener.dataReceived(address, msgType, from, size, wireType);
    }

    public void addNodeHandleFactoryListener(NodeHandleFactoryListener listener) {
        handleFactory.addNodeHandleFactoryListener(listener);
    }

    public void removeNodeHandleFactoryListener(NodeHandleFactoryListener listener) {
        handleFactory.removeNodeHandleFactoryListener(listener);
    }

    public void setNodeHandleFetcher(NodeHandleFetcher nodeHandleFetcher) {
        this.nodeHandleFetcher = nodeHandleFetcher;
    }

    public void getNodeHandle(Object o, Continuation<NodeHandle, Exception> c) {
        nodeHandleFetcher.getNodeHandle(o, c);
    }
}

