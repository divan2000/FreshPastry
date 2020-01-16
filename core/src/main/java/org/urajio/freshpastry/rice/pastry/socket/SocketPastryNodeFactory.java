package org.urajio.freshpastry.rice.pastry.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.ErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketCountListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi.CommonAPITransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi.IdFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi.OptionsAdder;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.limitsockets.LimitSocketsTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo.NetworkInfoTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo.ProbeStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.MinRTTProximityProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRouteForwardStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRouteTransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.factory.MultiAddressSourceRouteFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.SourceRouteManager;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.SourceRouteManagerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.SourceRouteStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.simple.NextHopStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.simple.SimpleSourceRouteStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.wire.WireTransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.wire.magicnumber.MagicNumberTransportLayer;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.processing.Processor;
import org.urajio.freshpastry.rice.environment.processing.simple.SimpleProcessor;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.environment.random.simple.SimpleRandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.Message;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleInputBuffer;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;
import org.urajio.freshpastry.rice.pastry.commonapi.PastryEndpointMessage;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.socket.nat.probe.ProbeApp;
import org.urajio.freshpastry.rice.pastry.standard.ProximityNeighborSelector;
import org.urajio.freshpastry.rice.pastry.transport.*;
import org.urajio.freshpastry.rice.selector.SelectorManager;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Pastry node factory for Socket-linked nodes.
 *
 * @author Alan Mislove
 * @version $Id: SocketPastryNodeFactory.java,v 1.6 2004/03/08 19:53:57 amislove
 * Exp $
 */
public class SocketPastryNodeFactory extends TransportPastryNodeFactory {
    /**
     * Maps to a MultiInetSocketAddress
     */
    public static final String PROXY_ADDRESS = "SocketPastryNodeFactory.proxyAddress";
    /**
     * Maps to a InetSocketAddressLookup
     */
    public static final String IP_SERVICE = "SocketPastryNodeFactory.ip-service";
    /**
     * Maps to MultiInetAddressTransportLayer
     */
    public static final String MULTI_INET_TL = "SocketPastryNodeFactory.milti-inet-tl";
    /**
     * maps to a PriorityTransportLayer<MultiInetSocketAddress>
     */
    public static final String PRIORITY_TL = "PriorityTransportLayer.PRIORITY_TL";
    public static final String MULTI_ADDRESS_STRATEGY = "SocketPastryNodeFactory.milti-inet-addressStrategy";
    public static final byte[] PASTRY_MAGIC_NUMBER = new byte[]{0x27, 0x40, 0x75, 0x3A};
    public static final String NODE_HANDLE_FACTORY = "SocketPastryNodeFactory.NODE_HANDLE_FACTORY";
    public static final byte NETWORK_INFO_NODE_HANDLE_INDEX = 1;
    private final static Logger logger = LoggerFactory.getLogger(SocketPastryNodeFactory.class);
    protected NodeIdFactory nidFactory;
    protected InetAddress localAddress;
    protected int testFireWallPolicy;

    protected int findFireWallPolicy;
    // the ordered list of InetAddresses, from External to internal
    InetAddress[] addressList;
    String firewallAppName;
    int firewallSearchTries;
    Map<PastryNode, LivenesSourceRouteForwardStrategy<MultiInetSocketAddress>> livenesSourceRouteForwardStrategy = new HashMap<>();
    private int port;

    public SocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
        this(nf, null, startPort, env);
    }

    public SocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env) throws IOException {
        super(env);

        environment = env;
        nidFactory = nf;
        port = startPort;
        Parameters params = env.getParameters();

        firewallSearchTries = params.getInt("nat_find_port_max_tries");
        firewallAppName = params.getString("nat_app_name");
        localAddress = bindAddress;
        if (localAddress == null) {
            if (params.contains("socket_bindAddress")) {
                localAddress = params.getInetAddress("socket_bindAddress");
            }
        }

        // user didn't specify localAddress via param or config file, ask OS
        if (localAddress == null) {
            localAddress = InetAddress.getLocalHost();

            Socket temp = null;
            ServerSocket test2 = null;

            if (localAddress.isLoopbackAddress() &&
                    !params.getBoolean("pastry_socket_allow_loopback")) {
                try {
                    // os gave us the loopback address, and the user doesn't want that

                    // try the internet
                    temp = new Socket(params.getString("pastry_socket_known_network_address"),
                            params.getInt("pastry_socket_known_network_address_port"));
                    if (temp.getLocalAddress().equals(localAddress))
                        throw new IllegalStateException("Cannot bind to " + localAddress + ":" + port);
                    localAddress = temp.getLocalAddress();
                    temp.close();
                    temp = null;

                    logger.warn("Error binding to default IP, using " + localAddress + ":" + port);

                    try {
                        test2 = new ServerSocket();
                        test2.bind(new InetSocketAddress(localAddress, port));
                    } catch (SocketException e2) {
                        throw new IllegalStateException("Cannot bind to " + localAddress + ":" + port, e2);
                    }
                } finally {

                    try {
                        if (test2 != null)
                            test2.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (temp != null)
                            temp.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // *************** Delme **************
    public static InetSocketAddress verifyConnection(int i, InetSocketAddress addr, InetSocketAddress[] addr2, Environment env) {
        return null;
    }

    public InetAddress getBindAddress() {
        return localAddress;
    }

    public InetSocketAddress getNextInetSocketAddress() {
        return new InetSocketAddress(localAddress, port);
    }

    @Override
    protected void registerApps(PastryNode pn, LeafSet leafSet, RoutingTable routeTable, NodeHandleAdapter nha, NodeHandleFactory handleFactory) {
        super.registerApps(pn, leafSet, routeTable, nha, handleFactory);

        ProbeStrategy probeStrategy = getProbeStrategy(pn);
    }

    protected ProbeStrategy getProbeStrategy(PastryNode pn) {
        NetworkInfoTransportLayer ipService = (NetworkInfoTransportLayer) pn.getVars().get(IP_SERVICE);
        MultiInetAddressTransportLayer tl = (MultiInetAddressTransportLayer) pn.getVars().get(MULTI_INET_TL);
        ProbeApp probeApp = new ProbeApp(pn, ipService, tl.getAddressStrategy());
        probeApp.register();
        ipService.setProbeStrategy(probeApp);
        return probeApp;
    }

    // ********************** abstract methods **********************
    @Override
    public NodeHandle getLocalHandle(PastryNode pn, NodeHandleFactory nhf) {
        SocketNodeHandleFactory pnhf = (SocketNodeHandleFactory) nhf;
        MultiInetSocketAddress proxyAddress = (MultiInetSocketAddress) pn.getVars().get(PROXY_ADDRESS);
        return pnhf.getNodeHandle(proxyAddress, pn.getEnvironment().getTimeSource().currentTimeMillis(), pn.getNodeId());
    }

    public NodeHandleFactory getNodeHandleFactory(PastryNode pn) {
        if (pn.getVars().containsKey(NODE_HANDLE_FACTORY)) {
            return (NodeHandleFactory) pn.getVars().get(NODE_HANDLE_FACTORY);
        }
        NodeHandleFactory ret = new SocketNodeHandleFactory(pn);
        pn.getVars().put(NODE_HANDLE_FACTORY, ret);
        return ret;
    }

    /**
     * This is split off so we can get the IpServiceLayer easily.
     *
     * @return
     */
    public TransportLayer<InetSocketAddress, ByteBuffer> getBottomLayers(PastryNode pn, MultiInetSocketAddress proxyAddress) throws IOException {
        // wire layer
        TransportLayer<InetSocketAddress, ByteBuffer> wtl = getWireTransportLayer(proxyAddress.getInnermostAddress(), pn);

        // magic number layer
        TransportLayer<InetSocketAddress, ByteBuffer> mntl = getMagicNumberTransportLayer(wtl, pn);

        // Limited sockets layer
        TransportLayer<InetSocketAddress, ByteBuffer> lstl = getLimitSocketsTransportLayer(mntl, pn);

        // Network Info layer

        return getIpServiceTransportLayer(lstl, pn);
    }

    public NodeHandleAdapter getNodeHandleAdapter(
            final PastryNode pn,
            NodeHandleFactory handleFactory2,
            TLDeserializer deserializer) throws IOException {

        Environment environment = pn.getEnvironment();

        SocketNodeHandle localhandle = (SocketNodeHandle) pn.getLocalHandle();
        final SocketNodeHandleFactory handleFactory = (SocketNodeHandleFactory) handleFactory2;
        MultiInetSocketAddress proxyAddress = localhandle.eaddress;
        MultiAddressSourceRouteFactory esrFactory = getMultiAddressSourceRouteFactory(pn);

        // Network Info layer
        TransportLayer<InetSocketAddress, ByteBuffer> iptl = getBottomLayers(pn, proxyAddress);

        // MultiInet layer
        TransportLayer<MultiInetSocketAddress, ByteBuffer> etl = getMultiAddressSourceRouteTransportLayer(iptl, pn, proxyAddress);
        pn.getVars().put(MULTI_INET_TL, etl);

        // SourceRoute<MultiInet> layer
        TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> srl = getSourceRouteTransportLayer(etl, pn, esrFactory);

        // Identity (who knows how to simplify this one...)
        IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress,
                ByteBuffer, SourceRoute<MultiInetSocketAddress>> identity = getIdentityImpl(pn, handleFactory);

        // LowerIdentity
        TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> lowerIdentityLayer = getLowerIdentityLayer(srl, pn, identity);

        // Liveness
        TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl = getLivenessTransportLayer(lowerIdentityLayer, pn);
        notifyLivenessTransportLayerConstructed(pn, ltl);

        // Source Route Manager
        TransLivenessProximity<MultiInetSocketAddress, ByteBuffer> srm = getSourceRouteManagerLayer(
                ltl.getTransportLayer(), ltl.getLivenessProvider(), ltl.getPinger(), pn, proxyAddress, esrFactory);

        // Priority
        PriorityTransportLayer<MultiInetSocketAddress> priorityTL = getPriorityTransportLayer(
                srm.getTransportLayer(), srm.getLivenessProvider(), srm.getProximityProvider(), pn);

        // UpperIdentiy
        TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentityLayer = getUpperIdentityLayer(
                priorityTL, pn, identity, srm.getLivenessProvider(), srm.getProximityProvider(), ltl.getOverrideLiveness());

        // CommonAPI
        TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> commonAPItl = getCommonAPITransportLayer(
                upperIdentityLayer.getTransportLayer(), pn, deserializer);

        return new NodeHandleAdapter(
                commonAPItl,
                upperIdentityLayer.getLivenessProvider(),
                upperIdentityLayer.getProximityProvider());
    }

    protected MultiAddressSourceRouteFactory getMultiAddressSourceRouteFactory(PastryNode pn) {
        return new MultiAddressSourceRouteFactory();
    }

    protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, final PastryNode pn) throws IOException {
        Environment environment = pn.getEnvironment();
        WireTransportLayerImpl wtl = new WireTransportLayerImpl(innermostAddress, environment, null);
        wtl.addSocketCountListener(getSocketCountListener(pn));

        return wtl;
    }

    protected SocketCountListener<InetSocketAddress> getSocketCountListener(final PastryNode pn) {
        return new SocketCountListener<InetSocketAddress>() {
            public void socketOpened(InetSocketAddress i,
                                     Map<String, Object> options, boolean outgoing) {
                pn.broadcastChannelOpened(i, 0);
            }

            public void socketClosed(InetSocketAddress i,
                                     Map<String, Object> options) {
                pn.broadcastChannelClosed(i);
            }
        };
    }

    protected TransportLayer<InetSocketAddress, ByteBuffer> getMagicNumberTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> wtl, PastryNode pn) {
        Environment environment = pn.getEnvironment();
        return new MagicNumberTransportLayer<>(wtl, environment, null, PASTRY_MAGIC_NUMBER, 5000);
    }

    protected TransportLayer<InetSocketAddress, ByteBuffer> getLimitSocketsTransportLayer(
            TransportLayer<InetSocketAddress, ByteBuffer> mntl, PastryNode pn) {
        Environment environment = pn.getEnvironment();
        return new LimitSocketsTransportLayer<>(environment.getParameters().getInt("pastry_socket_scm_max_open_sockets"), mntl, null, environment);
    }

    protected TransportLayer<InetSocketAddress, ByteBuffer> getIpServiceTransportLayer(
            TransportLayer<InetSocketAddress, ByteBuffer> wtl, final PastryNode pn) throws IOException {

        // make the network layer
        Environment environment = pn.getEnvironment();
        final NetworkInfoTransportLayer ipTL =
                new NetworkInfoTransportLayer(wtl, environment, null);
        pn.getVars().put(IP_SERVICE, ipTL);

        // install the NodeHandle as an ID
        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        pn.getLocalHandle().serialize(sob);
        ipTL.setId(NETWORK_INFO_NODE_HANDLE_INDEX, sob.getBytes());

        // install the NodeHandleFetcher for the NodeHandle
        pn.setNodeHandleFetcher(new NodeHandleFetcher() {
            public Cancellable getNodeHandle(Object o, final Continuation<NodeHandle, Exception> c) {
                InetSocketAddress addr = (InetSocketAddress) o;
                return ipTL.getId(addr, NETWORK_INFO_NODE_HANDLE_INDEX, new Continuation<byte[], IOException>() {

                    public void receiveResult(byte[] result) {
                        try {
                            NodeHandle nh = getNodeHandleFactory(pn).readNodeHandle(new SimpleInputBuffer(result));
                            c.receiveResult(nh);
                        } catch (IOException ioe) {
                            c.receiveException(ioe);
                        }
                    }

                    public void receiveException(IOException exception) {
                        c.receiveException(exception);
                    }
                }, null);
            }
        });
        return ipTL;
    }

    protected TransportLayer<MultiInetSocketAddress, ByteBuffer> getMultiAddressSourceRouteTransportLayer(
            TransportLayer<InetSocketAddress, ByteBuffer> mntl,
            PastryNode pn,
            MultiInetSocketAddress localAddress) {
        AddressStrategy adrStrat = new SimpleAddressStrategy();
        pn.getVars().put(MULTI_ADDRESS_STRATEGY, adrStrat);
        return new MultiInetAddressTransportLayerImpl(localAddress, mntl, pn.getEnvironment(), null, adrStrat);
    }

    protected TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getSourceRouteTransportLayer(
            TransportLayer<MultiInetSocketAddress, ByteBuffer> etl,
            PastryNode pn,
            MultiAddressSourceRouteFactory esrFactory) {
        Environment environment = pn.getEnvironment();
        return new SourceRouteTransportLayerImpl<>(esrFactory, etl, getSourceRouteForwardStrategy(pn, esrFactory), environment, null);
    }

    protected SourceRouteForwardStrategy<MultiInetSocketAddress> getSourceRouteForwardStrategy(
            PastryNode pn, MultiAddressSourceRouteFactory esrFactory) {
        LivenesSourceRouteForwardStrategy<MultiInetSocketAddress> ret = new LivenesSourceRouteForwardStrategy<>(esrFactory);
        livenesSourceRouteForwardStrategy.put(pn, ret);
        return ret;
    }

    private void notifyLivenessTransportLayerConstructed(PastryNode pn,
                                                         TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl) {
        LivenesSourceRouteForwardStrategy<MultiInetSocketAddress> srFs = livenesSourceRouteForwardStrategy.remove(pn);
        if (srFs != null) {
            srFs.setLivenessProvider(ltl.getLivenessProvider());
        }
    }

    protected IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> getIdentiySerializer(PastryNode pn, SocketNodeHandleFactory handleFactory) {
        return new SPNFIdentitySerializer(pn, handleFactory);
    }

    protected IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>,
            MultiInetSocketAddress, ByteBuffer,
            SourceRoute<MultiInetSocketAddress>>
    getIdentityImpl(final PastryNode pn, final SocketNodeHandleFactory handleFactory) throws IOException {
        Environment environment = pn.getEnvironment();
        SocketNodeHandle localhandle = (SocketNodeHandle) pn.getLocalHandle();

        IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> serializer =
                getIdentiySerializer(pn, handleFactory);

        SimpleOutputBuffer buf = new SimpleOutputBuffer();
        serializer.serialize(buf, localhandle);
        byte[] localHandleBytes = new byte[buf.getWritten()];
        System.arraycopy(buf.getBytes(), 0, localHandleBytes, 0, localHandleBytes.length);

        return new IdentityImpl<>(
                localHandleBytes, serializer,
                new NodeChangeStrategy<TransportLayerNodeHandle<MultiInetSocketAddress>>() {
                    public boolean canChange(
                            TransportLayerNodeHandle<MultiInetSocketAddress> oldDest,
                            TransportLayerNodeHandle<MultiInetSocketAddress> newDest) {
                        if (newDest.getAddress().equals(oldDest.getAddress())) {
                            logger.info("canChange(" + oldDest + "," + newDest + ")");
                            if (newDest.getEpoch() > oldDest.getEpoch()) {
                                logger.info("canChange(" + oldDest + ":" + oldDest.getEpoch() + "," + newDest + ":" + newDest.getEpoch() + "):true");
                                return true;
                            }
                        } else {
                            throw new RuntimeException("canChange(" + oldDest + "," + newDest + ") doesn't make any sense, these aren't comparable to eachother.");
                        }
                        logger.info("canChange(" + oldDest + ":" + oldDest.getEpoch() + "," + newDest + ":" + newDest.getEpoch() + "):false");
                        return false;

                    }
                },
                new SanityChecker<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress>() {

                    public boolean isSane(TransportLayerNodeHandle<MultiInetSocketAddress> upper,
                                          MultiInetSocketAddress middle) {
                        return upper.getAddress().equals(middle);
                    }

                },
                getBindStrategy(),
                environment);
    }

    protected BindStrategy<TransportLayerNodeHandle<MultiInetSocketAddress>, SourceRoute<MultiInetSocketAddress>> getBindStrategy() {
        return null;
    }

    protected TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getLowerIdentityLayer(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> srl,
            PastryNode pn,
            IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>,
                    MultiInetSocketAddress,
                    ByteBuffer,
                    SourceRoute<MultiInetSocketAddress>> identity) {

        identity.initLowerLayer(srl, null);
        return identity.getLowerIdentity();
    }

    protected TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getLivenessTransportLayer(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> tl,
            PastryNode pn) {
        Environment environment = pn.getEnvironment();
        int checkDeadThrottle = environment.getParameters().getInt("pastry_socket_srm_check_dead_throttle"); // 300000

        final LivenessTransportLayerImpl<SourceRoute<MultiInetSocketAddress>> ltl =
                new LivenessTransportLayerImpl<>(tl, environment, null, checkDeadThrottle);

        return new TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer>() {
            public TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getTransportLayer() {
                return ltl;
            }

            public LivenessProvider<SourceRoute<MultiInetSocketAddress>> getLivenessProvider() {
                return ltl;
            }

            public OverrideLiveness<SourceRoute<MultiInetSocketAddress>> getOverrideLiveness() {
                return ltl;
            }

            public Pinger<SourceRoute<MultiInetSocketAddress>> getPinger() {
                return ltl;
            }
        };
    }

    protected TransLivenessProximity<MultiInetSocketAddress, ByteBuffer> getSourceRouteManagerLayer(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl,
            LivenessProvider<SourceRoute<MultiInetSocketAddress>> livenessProvider,
            Pinger<SourceRoute<MultiInetSocketAddress>> pinger,
            PastryNode pn,
            MultiInetSocketAddress proxyAddress,
            MultiAddressSourceRouteFactory esrFactory) throws IOException {
        Environment environment = pn.getEnvironment();

        SourceRouteStrategy<MultiInetSocketAddress> srStrategy = getSourceRouteStrategy(ltl, livenessProvider, pinger, pn, proxyAddress, esrFactory);
        MinRTTProximityProvider<SourceRoute<MultiInetSocketAddress>> prox =
                new MinRTTProximityProvider<>(pinger, environment);
        final SourceRouteManager<MultiInetSocketAddress> srm =
                new SourceRouteManagerImpl<>(esrFactory, ltl, livenessProvider, prox, environment, srStrategy);
        return new TransLivenessProximity<MultiInetSocketAddress, ByteBuffer>() {
            public TransportLayer<MultiInetSocketAddress, ByteBuffer> getTransportLayer() {
                return srm;
            }

            public ProximityProvider<MultiInetSocketAddress> getProximityProvider() {
                return srm;
            }

            public LivenessProvider<MultiInetSocketAddress> getLivenessProvider() {
                return srm;
            }
        };
    }

    protected SourceRouteStrategy<MultiInetSocketAddress> getSourceRouteStrategy(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl,
            LivenessProvider<SourceRoute<MultiInetSocketAddress>> livenessProvider,
            Pinger<SourceRoute<MultiInetSocketAddress>> pinger,
            PastryNode pn,
            MultiInetSocketAddress proxyAddress,
            MultiAddressSourceRouteFactory esrFactory) throws IOException {

        NextHopStrategy<MultiInetSocketAddress> nhStrategy = getNextHopStrategy(ltl, livenessProvider, pinger, pn, proxyAddress, esrFactory);
        return new SimpleSourceRouteStrategy<>(proxyAddress, esrFactory, nhStrategy, environment);
    }

    protected NextHopStrategy<MultiInetSocketAddress> getNextHopStrategy(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl,
            LivenessProvider<SourceRoute<MultiInetSocketAddress>> livenessProvider,
            Pinger<SourceRoute<MultiInetSocketAddress>> pinger,
            PastryNode pn,
            MultiInetSocketAddress proxyAddress,
            MultiAddressSourceRouteFactory esrFactory) {

        return new LeafSetNHStrategy(pn.getLeafSet());
    }

    protected PriorityTransportLayer<MultiInetSocketAddress> getPriorityTransportLayer(TransportLayer<MultiInetSocketAddress, ByteBuffer> trans, LivenessProvider<MultiInetSocketAddress> liveness, ProximityProvider<MultiInetSocketAddress> prox, PastryNode pn) {
        Environment environment = pn.getEnvironment();
        PriorityTransportLayer<MultiInetSocketAddress> priorityTL =
                new PriorityTransportLayerImpl<>(
                        trans,
                        liveness,
                        prox,
                        environment,
                        environment.getParameters().getInt("pastry_socket_writer_max_msg_size"),
                        environment.getParameters().getInt("pastry_socket_writer_max_queue_length"),
                        null);
        pn.getVars().put(PRIORITY_TL, priorityTL);

        return priorityTL;
    }

    protected TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> getUpperIdentityLayer(
            TransportLayer<MultiInetSocketAddress, ByteBuffer> priorityTL,
            PastryNode pn,
            IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>,
                    MultiInetSocketAddress,
                    ByteBuffer,
                    SourceRoute<MultiInetSocketAddress>> identity,
            LivenessProvider<MultiInetSocketAddress> live,
            ProximityProvider<MultiInetSocketAddress> prox,
            OverrideLiveness<SourceRoute<MultiInetSocketAddress>> overrideLiveness) {

        SocketNodeHandle localhandle = (SocketNodeHandle) pn.getLocalHandle();
        identity.initUpperLayer(localhandle, priorityTL, live, prox, overrideLiveness);
        final UpperIdentity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentityLayer = identity.getUpperIdentity();
        return new TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer>() {
            public TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> getTransportLayer() {
                return upperIdentityLayer;
            }

            public ProximityProvider<TransportLayerNodeHandle<MultiInetSocketAddress>> getProximityProvider() {
                return upperIdentityLayer;
            }

            public LivenessProvider<TransportLayerNodeHandle<MultiInetSocketAddress>> getLivenessProvider() {
                return upperIdentityLayer;
            }
        };
    }

    protected TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> getCommonAPITransportLayer(
            TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentity,
            PastryNode pn, TLDeserializer deserializer) {
        final Environment environment = pn.getEnvironment();
        IdFactory idFactory = new IdFactory() {
            public org.urajio.freshpastry.rice.p2p.commonapi.Id build(InputBuffer buf) throws IOException {
                return Id.build(buf);
            }
        };

        return new CommonAPITransportLayerImpl<>(
                upperIdentity,
                idFactory,
                deserializer,
                getOptionsAdder(pn),
                new ErrorHandler<>() {

                    public void receivedUnexpectedData(
                            TransportLayerNodeHandle<MultiInetSocketAddress> id, byte[] bytes,
                            int location, Map<String, Object> options) {
                        if (logger.isWarnEnabled()) {
                            // make this pretty
                            String s = "";
                            int numBytes = 8;
                            if (bytes.length < numBytes) numBytes = bytes.length;
                            for (int i = 0; i < numBytes; i++) {
                                s += bytes[i] + ",";
                            }
                            logger.warn("Unexpected data from " + id + " " + s);
                        }
                    }

                    public void receivedException(TransportLayerNodeHandle<MultiInetSocketAddress> i, Throwable error) {
                        if (logger.isInfoEnabled()) {
                            if (error instanceof NodeIsFaultyException) {
                                NodeIsFaultyException nife = (NodeIsFaultyException) error;
                                logger.info("Dropping message " + nife.getAttemptedMessage() + " to " + nife.getIdentifier() + " because it is faulty.");
                                if (i.isAlive()) {
                                    logger.info("NodeIsFaultyException thrown for non-dead node. " + i + " " + i.getLiveness(), nife);
                                }
                            }
                        }
                    }
                },
                environment);
    }

    protected OptionsAdder getOptionsAdder(PastryNode pn) {
        byte[] foo = pn.getId().toByteArray();

        return new OptionsAdder() {

            public Map<String, Object> addOptions(Map<String, Object> options,
                                                  RawMessage m1) {
                Message m = m1;
                if (m instanceof RouteMessage) {
                    RouteMessage rm = (RouteMessage) m;
                    m = rm.internalMsg;
                    if (m == null) m = rm;
                }
                if (m instanceof PastryEndpointMessage) {
                    PastryEndpointMessage pem = (PastryEndpointMessage) m;
                    m = pem.getMessage();
                    options = OptionsFactory.addOption(options, CommonAPITransportLayerImpl.MSG_ADDR, pem.getDestination());
                }
                if (m instanceof org.urajio.freshpastry.rice.pastry.messaging.Message) {
                    org.urajio.freshpastry.rice.pastry.messaging.Message pm = (org.urajio.freshpastry.rice.pastry.messaging.Message) m;
                    options = OptionsFactory.addOption(options, CommonAPITransportLayerImpl.MSG_ADDR, pm.getDestination());
                }
                if (m instanceof RawMessage) {
                    RawMessage rm = (RawMessage) m;
                    options = OptionsFactory.addOption(options, CommonAPITransportLayerImpl.MSG_TYPE, rm.getType());
                }

                return OptionsFactory.addOption(options, CommonAPITransportLayerImpl.MSG_STRING, m.toString(), CommonAPITransportLayerImpl.MSG_CLASS, m.getClass().getName());
            }
        };
    }

    @Override
    protected Bootstrapper getBootstrapper(PastryNode pn,
                                           NodeHandleAdapter tl,
                                           NodeHandleFactory handleFactory,
                                           ProximityNeighborSelector pns) {

        return new TLBootstrapper(pn, tl.getTL(), (SocketNodeHandleFactory) handleFactory, pns);
    }

    public NodeHandle getNodeHandle(InetSocketAddress bootstrap, int i) {
        return getNodeHandle(bootstrap);
    }

    public NodeHandle getNodeHandle(InetSocketAddress bootstrap) {
        return new BogusNodeHandle(bootstrap);
    }

    public void getNodeHandle(InetSocketAddress[] bootstraps, Continuation<NodeHandle, Exception> c) {
        c.receiveResult(getNodeHandle(bootstraps, 0));
    }

    public NodeHandle getNodeHandle(InetSocketAddress[] bootstraps, int int1) {
        return new BogusNodeHandle(bootstraps);
    }

    /**
     * Method which creates a Pastry node from the next port with a randomly
     * generated NodeId.
     *
     * @param bootstrap Node handle to bootstrap from.
     * @return A node with a random ID and next port number.
     */
    public PastryNode newNode(NodeHandle bootstrap) {
        return newNode(bootstrap, nidFactory.generateNodeId());
    }

    /**
     * Method which creates a Pastry node from the next port with the specified nodeId
     * (or one generated from the NodeIdFactory if not specified)
     *
     * @param bootstrap Node handle to bootstrap from.
     * @return A node with a random ID and next port number.
     */
    public PastryNode newNode(NodeHandle bootstrap, InetSocketAddress proxy) {
        return newNode(bootstrap, nidFactory.generateNodeId(), proxy);
    }

    /**
     * Method which creates a Pastry node from the next port with the specified nodeId
     * (or one generated from the NodeIdFactory if not specified)
     *
     * @param bootstrap Node handle to bootstrap from.
     * @param nodeId    if non-null, will use this nodeId for the node, rather than using the NodeIdFactory
     * @return A node with a random ID and next port number.
     */
    public PastryNode newNode(final NodeHandle bootstrap, Id nodeId) {
        return newNode(bootstrap, nodeId, null);
    }

    /**
     * Need to boot manually.
     * <p>
     * n.getBootstrapper().boot(addresses);
     *
     * @return
     */
    public PastryNode newNode() {
        return newNode(nidFactory.generateNodeId(), (InetSocketAddress) null);
    }

    public PastryNode newNode(Id id) {
        return newNode(id, (InetSocketAddress) null);
    }

    public PastryNode newNode(InetSocketAddress proxyAddress) {
        return newNode(nidFactory.generateNodeId(), proxyAddress);
    }

    public PastryNode newNode(NodeHandle nodeHandle, Id id, InetSocketAddress proxyAddress) {
        PastryNode n = newNode(id, proxyAddress);
        if (nodeHandle == null) {
            n.getBootstrapper().boot(null);
        } else {
            BogusNodeHandle bnh = (BogusNodeHandle) nodeHandle;
            n.getBootstrapper().boot(bnh.addresses);

        }
        return n;
    }

    /**
     * This method uses the pAddress as the outer address if it's non-null.
     * It automatically generates the internal address from the localAddress, and increments the port as necessary.
     *
     * @param nodeId
     * @param pAddress
     * @return
     */
    public synchronized PastryNode newNode(final Id nodeId, InetSocketAddress pAddress) {
        if (pAddress == null) {
            if (environment.getParameters().contains("external_address")) {
                try {
                    pAddress = environment.getParameters().getInetSocketAddress("external_address");
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        try {
            MultiInetSocketAddress multiAddress;
            if (pAddress == null) {
                multiAddress = new MultiInetSocketAddress(new InetSocketAddress(localAddress, port));
            } else {
                multiAddress = new MultiInetSocketAddress(pAddress, new InetSocketAddress(localAddress, port));
            }
            PastryNode ret = newNode(nodeId, multiAddress); // fix the method just
            if (environment.getParameters().getBoolean(
                    "pastry_socket_increment_port_after_construction")) {
                port++;
            }
            return ret;

            // below if you change
            // this
        } catch (BindException e) {
            logger.warn("Warning: ", e);

            if (environment.getParameters().getBoolean(
                    "pastry_socket_increment_port_after_construction")) {
                port++;
                try {
                    return newNode(nodeId, pAddress); // recursion, this will
                    // prevent from things
                    // getting too out of
                    // hand in
                    // case the node can't bind to anything, expect a
                    // StackOverflowException
                } catch (StackOverflowError soe) {
                    logger.error("SEVERE: SocketPastryNodeFactory: Could not bind on any ports!" + soe);
                    throw soe;
                }
            } else {
                // clean up Environment
                if (this.environment.getParameters().getBoolean(
                        "pastry_factory_multipleNodes")) {
                    environment.destroy();
                }

                throw new RuntimeException(e);
            }
        } catch (IOException ioe) {

            throw new RuntimeException(ioe);
        }
    }

    /**
     * Method which creates a Pastry node from the next port with the specified
     * nodeId (or one generated from the NodeIdFactory if not specified)
     *
     * @param nodeId   if non-null, will use this nodeId for the node, rather than using
     *                 the NodeIdFactory
     * @param pAddress The address to claim that this node is at - used for proxies
     *                 behind NATs
     * @return A node with a random ID and next port number.
     */
    public synchronized PastryNode newNode(final Id nodeId, final MultiInetSocketAddress pAddress) throws IOException {
        /*
         * This code fixes a bug on some combinations of linux/java that causes binding to the socket
         * to deadlock.  By putting this on the selector thread, we make sure the selector is not
         * selecting.
         */
        final ArrayList<PastryNode> pn = new ArrayList<>(1);
        final ArrayList<IOException> re = new ArrayList<>(1);
        Runnable r = new Runnable() {

            public void run() {
                synchronized (pn) {
                    newNodeSelector(nodeId, pAddress, new Continuation<PastryNode, IOException>() {

                        public void receiveResult(PastryNode node) {
                            synchronized (pn) {
                                pn.add(node);
                                pn.notify();
                            }
                        }

                        public void receiveException(IOException exception) {
                            synchronized (pn) {
                                re.add(exception);
                                pn.notify();
                            }
                        }
                    }, null);
                }
            }
        };
        if (environment.getSelectorManager().isSelectorThread()) {
            r.run();
        } else {
            environment.getSelectorManager().invoke(r);
        }
        synchronized (pn) {
            if (pn.isEmpty() && re.isEmpty()) {
                try {
                    pn.wait();
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        }
        if (pn.isEmpty()) {
            throw re.get(0);
        }
        return pn.get(0);
    }

    protected void newNodeSelector(Id nodeId,
                                   MultiInetSocketAddress proxyAddress, Continuation<PastryNode, IOException> deliverResultToMe, Map<String, Object> initialVars) {

        try {
            // this code builds a different environment for each PastryNode
            Environment environment = cloneEnvironment(this.environment, nodeId);

            Parameters params = environment.getParameters();

            PastryNode pn = new PastryNode(nodeId, environment);
            if (initialVars != null) pn.getVars().putAll(initialVars);
            pn.getVars().put(PROXY_ADDRESS, proxyAddress);

            nodeHandleHelper(pn);

            deliverResultToMe.receiveResult(pn);
        } catch (IOException ioe) {
            deliverResultToMe.receiveException(ioe);
        }
    }

    protected Environment cloneEnvironment(Environment rootEnvironment, Id nodeId) {
        Environment ret = rootEnvironment;
        if (rootEnvironment.getParameters().getBoolean("pastry_factory_multipleNodes")) {

            // new selector
            SelectorManager sman = cloneSelectorManager(rootEnvironment, nodeId);

            // new processor
            Processor proc = cloneProcessor(rootEnvironment, nodeId);

            // new random source
            RandomSource rand = cloneRandomSource(rootEnvironment, nodeId);

            // build the environment
            ret = new Environment(sman, proc, rand, rootEnvironment.getTimeSource(),
                    rootEnvironment.getParameters(), rootEnvironment.getExceptionStrategy());

            // gain shared fate with the rootEnvironment
            rootEnvironment.addDestructable(ret);
        }
        return ret;
    }

    protected SelectorManager cloneSelectorManager(Environment rootEnvironment, Id nodeId) {
        SelectorManager sman = rootEnvironment.getSelectorManager();
        if (rootEnvironment.getParameters().getBoolean("pastry_factory_selectorPerNode")) {
            sman = new SelectorManager(nodeId.toString() + " Selector", rootEnvironment.getTimeSource(), rootEnvironment.getRandomSource());
        }
        return sman;
    }

    protected Processor cloneProcessor(Environment rootEnvironment, Id nodeId) {
        Processor proc = rootEnvironment.getProcessor();
        if (rootEnvironment.getParameters().getBoolean("pastry_factory_processorPerNode")) {
            proc = new SimpleProcessor(nodeId.toString() + " Processor");
        }

        return proc;
    }

    protected RandomSource cloneRandomSource(Environment rootEnvironment, Id nodeId) {
        long randSeed = rootEnvironment.getRandomSource().nextLong();
        return new SimpleRandomSource(randSeed);
    }

    /**
     * Method which constructs an InetSocketAddres for the local host with the
     * specifed port number.
     *
     * @param portNumber The port number to create the address at.
     * @return An InetSocketAddress at the localhost with port portNumber.
     */
    private MultiInetSocketAddress getEpochAddress(int portNumber) {
        MultiInetSocketAddress result = null;

        result = new MultiInetSocketAddress(new InetSocketAddress(localAddress,
                portNumber));
        return result;
    }

    protected interface TransLiveness<Identifier, MessageType> {
        TransportLayer<Identifier, MessageType> getTransportLayer();

        LivenessProvider<Identifier> getLivenessProvider();

        OverrideLiveness<Identifier> getOverrideLiveness();

        Pinger<Identifier> getPinger();
    }

    protected interface TransLivenessProximity<Identifier, MessageType> {
        TransportLayer<Identifier, ByteBuffer> getTransportLayer();

        LivenessProvider<Identifier> getLivenessProvider();

        ProximityProvider<Identifier> getProximityProvider();
    }

    public class TLBootstrapper implements Bootstrapper<InetSocketAddress> {
        // we need to store this in a mutable final object
        final List<LivenessListener<NodeHandle>> listener = new ArrayList<>();
        protected PastryNode pn;
        protected TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl;
        protected SocketNodeHandleFactory handleFactory;
        protected ProximityNeighborSelector pns;

        public TLBootstrapper(PastryNode pn,
                              TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl,
                              SocketNodeHandleFactory handleFactory,
                              ProximityNeighborSelector pns) {
            this.pn = pn;
            this.tl = tl;
            this.handleFactory = handleFactory;
            this.pns = pns;
        }

        protected void bootAsBootstrap() {
            pn.doneNode(Collections.EMPTY_LIST);
        }

        /**
         * This method is a bit out of order to make it work on any thread.  The method itself is non-blocking.
         * <p>
         * The problem is that we don't have any NodeHandles yet, only Ip addresses.  So, we're going to check liveness on a
         * bogus node handle, and when the WrongEpochAddress message comes back, the bogus node will be faulty, but a new
         * node (the real NodeHandle at that address) will come alive.  We can discover this by installing a LivenessListener
         * on the transport layer.
         * <p>
         * Here is what happens:
         * Create the "bogus" NodeHandles and store them in tempBootHandles
         * Register LivenessListener on transport layer.
         * Ping all of the "bogus" NodeHandles
         * When liveness changes to Non-Faulty it means we have found a new node.
         * When we collect all of the bootaddresses, or there is a timeout, we call continuation.receiveResult()
         * Continuation.receiveResult() unregisters the LivenessListener and then calls ProximityNeighborSelection.nearNodes()
         * PNS.nearNodes() calls into the continuation which calls PastryNode.doneNode()
         */
        public void boot(Collection<InetSocketAddress> bootaddresses_temp) {
            logger.debug("boot(" + bootaddresses_temp + ")");
            final Collection<InetSocketAddress> bootaddresses;
            if (bootaddresses_temp == null) {
                bootaddresses = Collections.EMPTY_LIST;
            } else {
                bootaddresses = bootaddresses_temp;
            }

            final boolean seed = environment.getParameters().getBoolean("rice_socket_seed") || bootaddresses.isEmpty() || bootaddresses.contains(((SocketNodeHandle) pn.getLocalHandle()).getAddress().getInnermostAddress());

            if (bootaddresses.isEmpty() ||
                    (bootaddresses.size() == 1 && seed)) {
                logger.info("boot() calling pn.doneNode(empty)");
                bootAsBootstrap();
                return;
            }

            // bogus handles
            final Collection<SocketNodeHandle> tempBootHandles = new ArrayList<>(bootaddresses.size());

            // real handles
            final Collection<org.urajio.freshpastry.rice.pastry.NodeHandle> bootHandles = new HashSet<>();

            TransportLayerNodeHandle<MultiInetSocketAddress> local = tl.getLocalIdentifier();
            InetSocketAddress localAddr = local.getAddress().getInnermostAddress();

            // fill in tempBootHandles
            for (InetSocketAddress addr : bootaddresses) {
                logger.debug("addr:" + addr + " local:" + localAddr);
                if (!addr.equals(localAddr)) {
                    tempBootHandles.add(getTempNodeHandle(addr));
                }
            }

            // this is the end of the task, but we have to declare it here
            final Continuation<Collection<NodeHandle>, Exception> beginPns = new Continuation<>() {
                boolean done = false; // to make sure this is only called once

                /**
                 * This is usually going to get called twice.  The first time when bootHandles is complete,
                 * the second time on a timeout.
                 *
                 */
                public void receiveResult(Collection<NodeHandle> initialSet) {
                    // make sure this only gets called once
                    if (done) return;
                    done = true;
                    logger.debug("boot() beginning pns with " + initialSet);

                    // remove the listener
                    pn.getLivenessProvider().removeLivenessListener(listener.get(0));

                    // do proximity neighbor selection
                    pns.getNearHandles(initialSet, new Continuation<Collection<NodeHandle>, Exception>() {

                        public void receiveResult(Collection<NodeHandle> result) {
                            // done!!!
                            if (!seed && result.isEmpty()) {
                                pn.joinFailed(new JoinFailedException("Cannot join ring.  All bootstraps are faulty." + bootaddresses));
                                return;
                            }
                            logger.info("boot() calling pn.doneNode(" + result + ")");
                            pn.doneNode(result);
                        }

                        public void receiveException(Exception exception) {
                            // TODO Auto-generated method stub
                        }
                    });
                }

                public void receiveException(Exception exception) {
                    // TODO Auto-generated method stub
                }
            };


            // Create the listener for the "real" nodes coming online based on WrongAddress messages from the "Bogus" ones
            listener.add(
                    new LivenessListener<NodeHandle>() {
                        public void livenessChanged(NodeHandle i2, int val, Map<String, Object> options) {
                            SocketNodeHandle i = (SocketNodeHandle) i2;
                            logger.debug("livenessChanged(" + i + "," + val + ")");
                            if (val <= LIVENESS_SUSPECTED && i.getEpoch() != -1L) {
                                boolean complete = false;

                                // add the new handle
                                synchronized (bootHandles) {
                                    bootHandles.add(i);
                                    if (bootHandles.size() == tempBootHandles.size()) {
                                        complete = true;
                                    }
                                }
                                if (complete) {
                                    beginPns.receiveResult(bootHandles);
                                }
                            }
                        }
                    });

            logger.debug("boot() adding liveness listener");
            // register the listener
            pn.getLivenessProvider().addLivenessListener(listener.get(0));

            logger.debug("boot() checking liveness");
            // check liveness on the bogus nodes
            for (SocketNodeHandle h : tempBootHandles) {
                checkLiveness(h, null);
            }

            // need to synchronize, because this can be called on any thread
            synchronized (bootHandles) {
                if (bootHandles.size() < tempBootHandles.size()) {
                    // only wait 10 seconds for the nodes
                    environment.getSelectorManager().schedule(new TimerTask() {

                        @Override
                        public void run() {
                            logger.debug("boot() timer expiring, attempting to start pns (it may have already started)");

                            beginPns.receiveResult(bootHandles);
                        }
                    }, 20000);
                }
            }

            // the root node (no boot addresses)
            if (tempBootHandles.isEmpty()) {
                logger.debug("invoking receiveResult (this is probably the first node in the ring)");
                environment.getSelectorManager().invoke(new Runnable() {
                    public void run() {
                        beginPns.receiveResult(bootHandles);
                    }
                });
            }

            logger.debug("boot() returning");
        }

        /**
         * Used as a helper to TLBootstrapper.boot() to generate a temporary node handle
         *
         * @param addr
         * @return
         */
        protected SocketNodeHandle getTempNodeHandle(InetSocketAddress addr) {
            return handleFactory.getNodeHandle(new MultiInetSocketAddress(addr), -1, Id.build());
        }

        protected void checkLiveness(SocketNodeHandle h, Map<String, Object> options) {
            pn.getLivenessProvider().checkLiveness(h, null);
        }
    }
}
