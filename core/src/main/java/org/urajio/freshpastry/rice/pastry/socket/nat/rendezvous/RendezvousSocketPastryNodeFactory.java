package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;
import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.IdentityImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.IdentitySerializer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.Pinger;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.AddressStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.SimpleAddressStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.nat.FirewallTLImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.factory.MultiAddressSourceRouteFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.simple.NextHopStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;
import org.urajio.freshpastry.rice.pastry.join.JoinProtocol;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandle;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.socket.SocketPastryNodeFactory;
import org.urajio.freshpastry.rice.pastry.socket.TransportLayerNodeHandle;
import org.urajio.freshpastry.rice.pastry.standard.ProximityNeighborSelector;
import org.urajio.freshpastry.rice.pastry.standard.StandardRouter;
import org.urajio.freshpastry.rice.pastry.transport.NodeHandleAdapter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;


/**
 * This class assembles the rendezvous layer with the rendezvous app.
 * <p>
 * Need to think about where this best goes, but for now, we'll put it just above the magic number layer.
 *
 * @author Jeff Hoye
 */
public class RendezvousSocketPastryNodeFactory extends SocketPastryNodeFactory {
    /**
     * maps to a RendezvousStrategy<RendezvousSocketNodeHandle>
     */
    public static final String RENDEZVOUS_STRATEGY = "RendezvousSocketPastryNodeFactory.RENDEZVOUS_STRATEGY";
    /**
     * maps to a RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle>
     */
    public static final String RENDEZVOUS_TL = "RendezvousSocketPastryNodeFactory.RENDEZVOUS_TL";
    /**
     * maps to a boolean, if true then simulates a firewall
     */
    public static final String SIMULATE_FIREWALL = "rendezvous_simulate_firewall";
    /**
     * getVars() maps to a ContactDirectStrategy<RendezvousSocketNodeHandle>
     */
    public static final String RENDEZVOUS_CONTACT_DIRECT_STRATEGY = "RendezvousSocketPastryNodeFactory.ContactDirectStrategy";
    private final static Logger logger = LoggerFactory.getLogger(RendezvousSocketPastryNodeFactory.class);
    /**
     * Maps to a byte contactState
     */
    protected String CONTACT_STATE = "RendezvousSocketPastryNodeFactory.CONTACT_STATE";
    protected RandomSource random;
    /**
     * The local node's contact state.
     * <p>
     * TODO: Configure this
     */
    byte localContactState = RendezvousSocketNodeHandle.CONTACT_DIRECT;
    /**
     * Used with getWireTL to make sure to return the bootstrap as not firewalled.
     */
    boolean firstNode = true;

    public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env, boolean firewalled) throws IOException {
        super(nf, bindAddress, startPort, env);
        init(firewalled);
    }

    public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env, boolean firewalled) throws IOException {
        super(nf, startPort, env);
        init(firewalled);
    }

    private void init(boolean firewalled) {
        random = environment.getRandomSource();
        if (firewalled) setContactState(RendezvousSocketNodeHandle.CONTACT_FIREWALLED);
    }

    public void setContactState(byte contactState) {
        this.localContactState = contactState;
    }

    /**
     * Can override the contactState on a per-node basis
     *
     * @param nodeId
     * @param proxyAddress
     * @param deliverResultToMe
     * @param initialVars
     * @param firewalled
     */
    protected void newNodeSelector(Id nodeId,
                                   MultiInetSocketAddress proxyAddress,
                                   Continuation<PastryNode, IOException> deliverResultToMe,
                                   Map<String, Object> initialVars, boolean firewalled) {

        byte contactState = localContactState;
        if (firewalled) {
            contactState = RendezvousSocketNodeHandle.CONTACT_FIREWALLED;
        } else {
            contactState = RendezvousSocketNodeHandle.CONTACT_DIRECT;
        }
        newNodeSelector(nodeId, proxyAddress, deliverResultToMe, initialVars, contactState);
    }

    /**
     * Can override the contactState on a per-node basis
     *
     * @param nodeId
     * @param proxyAddress
     * @param deliverResultToMe
     * @param initialVars
     * @param contactState
     */
    protected void newNodeSelector(Id nodeId,
                                   MultiInetSocketAddress proxyAddress,
                                   Continuation<PastryNode, IOException> deliverResultToMe,
                                   Map<String, Object> initialVars, byte contactState) {
        initialVars = OptionsFactory.addOption(initialVars, CONTACT_STATE, contactState);
        super.newNodeSelector(nodeId, proxyAddress, deliverResultToMe, initialVars);
    }

    @Override
    protected JoinProtocol getJoinProtocol(PastryNode pn, LeafSet leafSet,
                                           RoutingTable routeTable, ReadyStrategy lsProtocol) {
        RendezvousJoinProtocol jProtocol = new RendezvousJoinProtocol(pn,
                pn.getLocalHandle(), routeTable, leafSet, lsProtocol, (PilotManager<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_TL));
        jProtocol.register();
        return jProtocol;
    }

    @Override
    protected TransportLayer<InetSocketAddress, ByteBuffer> getIpServiceTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> wtl, PastryNode pn) throws IOException {
        TransportLayer<InetSocketAddress, ByteBuffer> mtl = super.getIpServiceTransportLayer(wtl, pn);

        if (pn.getLocalHandle() == null) return mtl;

        return getRendezvousTransportLayer(mtl, pn);
    }

    @Override
    protected IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> getIdentiySerializer(PastryNode pn, SocketNodeHandleFactory handleFactory) {
        return new RendezvousSPNFIdentitySerializer(pn, handleFactory);
    }

    protected TransportLayer<InetSocketAddress, ByteBuffer> getRendezvousTransportLayer(
            TransportLayer<InetSocketAddress, ByteBuffer> mtl, PastryNode pn) {

        RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle> ret =
                new RendezvousTransportLayerImpl<>(
                        mtl,
                        IdentityImpl.NODE_HANDLE_FROM_INDEX,
                        (RendezvousSocketNodeHandle) pn.getLocalHandle(),
                        getContactDeserializer(pn),
                        getRendezvousGenerator(pn),
                        getPilotFinder(pn),
                        getRendezvousStrategyHelper(pn),
                        getResponseStrategy(pn),
                        getContactDirectStrategy(pn),
                        pn.getEnvironment());

        pn.getVars().put(RENDEZVOUS_TL, ret);
        ((RendezvousStrategy<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_STRATEGY)).setTransportLayer(ret);

        generatePilotStrategy(pn, ret);
        return ret;
    }

    @Override
    protected NextHopStrategy<MultiInetSocketAddress> getNextHopStrategy(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl,
            LivenessProvider<SourceRoute<MultiInetSocketAddress>> livenessProvider,
            Pinger<SourceRoute<MultiInetSocketAddress>> pinger,
            PastryNode pn,
            MultiInetSocketAddress proxyAddress,
            MultiAddressSourceRouteFactory esrFactory) {

        return new RendezvousLeafSetNHStrategy(pn.getLeafSet());
    }

    protected ResponseStrategy<InetSocketAddress> getResponseStrategy(PastryNode pn) {
        return new TimeoutResponseStrategy<>(3000, pn.getEnvironment());
    }

    protected ContactDirectStrategy<RendezvousSocketNodeHandle> getContactDirectStrategy(PastryNode pn) {
        AddressStrategy adrStrat = (AddressStrategy) pn.getVars().get(MULTI_ADDRESS_STRATEGY);
        if (adrStrat == null) {
            adrStrat = new SimpleAddressStrategy();
        }

        ContactDirectStrategy<RendezvousSocketNodeHandle> ret = new RendezvousContactDirectStrategy((RendezvousSocketNodeHandle) pn.getLocalNodeHandle(), adrStrat, pn.getEnvironment());
        pn.getVars().put(RENDEZVOUS_CONTACT_DIRECT_STRATEGY, ret);

        return ret;
    }

    protected PilotFinder<RendezvousSocketNodeHandle> getPilotFinder(PastryNode pn) {
        return new LeafSetPilotFinder(pn);
    }

    protected void generatePilotStrategy(PastryNode pn, RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle> rendezvousLayer) {
        // only do this if firewalled
        RendezvousSocketNodeHandle handle = (RendezvousSocketNodeHandle) pn.getLocalHandle();
        if (handle != null && !handle.canContactDirect()) {
            new LeafSetPilotStrategy<>(pn.getLeafSet(), rendezvousLayer);
        }
    }

    protected ContactDeserializer<InetSocketAddress, RendezvousSocketNodeHandle> getContactDeserializer(final PastryNode pn) {
        return new ContactDeserializer<InetSocketAddress, RendezvousSocketNodeHandle>() {

            public Map<String, Object> getOptions(RendezvousSocketNodeHandle high) {
                return OptionsFactory.addOption(null, IdentityImpl.NODE_HANDLE_FROM_INDEX, high);
            }

            public RendezvousSocketNodeHandle deserialize(InputBuffer sib) throws IOException {
                return (RendezvousSocketNodeHandle) pn.readNodeHandle(sib);
            }

            public InetSocketAddress convert(RendezvousSocketNodeHandle high) {
                // TODO: is this the correct one?
                return high.eaddress.getAddress(0);
            }

            public void serialize(RendezvousSocketNodeHandle i, OutputBuffer buf) throws IOException {
                i.serialize(buf);
            }

            public ByteBuffer serialize(RendezvousSocketNodeHandle i)
                    throws IOException {
                SimpleOutputBuffer sob = new SimpleOutputBuffer();
                serialize(i, sob);
                return sob.getByteBuffer();
            }
        };
    }

    protected RendezvousGenerationStrategy<RendezvousSocketNodeHandle> getRendezvousGenerator(PastryNode pn) {
        return null;
    }

    @Override
    protected ProximityNeighborSelector getProximityNeighborSelector(PastryNode pn) {
        if (environment.getParameters().getBoolean("transport_use_pns")) {
            RendezvousPNSApplication pns = new RendezvousPNSApplication(pn, (ContactDirectStrategy<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_CONTACT_DIRECT_STRATEGY));
            pns.register();
            return pns;
        }

        // do nothing
        return new ProximityNeighborSelector() {
            public Cancellable getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
                deliverResultToMe.receiveResult(bootHandles);
                return null;
            }
        };
    }

    /**
     * This is an annoying hack.  We can't register the RendezvousApp until registerApps(), but we need it here.
     * <p>
     * This table temporarily holds the rendezvousApps until they are needed, then it is deleted.
     */
    protected RendezvousStrategy<RendezvousSocketNodeHandle> getRendezvousStrategyHelper(PastryNode pn) {
        RendezvousStrategy<RendezvousSocketNodeHandle> app = getRendezvousStrategy(pn);
        pn.getVars().put(RENDEZVOUS_STRATEGY, app);
        return app;
    }

    protected RendezvousStrategy<RendezvousSocketNodeHandle> getRendezvousStrategy(PastryNode pn) {
        return new RendezvousApp(pn);
    }

    @Override
    protected void registerApps(PastryNode pn, LeafSet leafSet, RoutingTable routeTable, NodeHandleAdapter nha, NodeHandleFactory handleFactory) {
        super.registerApps(pn, leafSet, routeTable, nha, handleFactory);
        RendezvousStrategy<RendezvousSocketNodeHandle> app = (RendezvousStrategy<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_STRATEGY);
        if (app instanceof RendezvousApp) {
            ((RendezvousApp) app).register();
        }
    }

    @Override
    public NodeHandleFactory getNodeHandleFactory(PastryNode pn) {
        return new RendezvousSNHFactory(pn);
    }

    @Override
    public NodeHandle getLocalHandle(PastryNode pn, NodeHandleFactory nhf) {
        byte contactState = localContactState;

        if (pn.getVars().containsKey(CONTACT_STATE)) {
            contactState = (Byte) pn.getVars().get(CONTACT_STATE);
        }

        // this code is for testing
        Parameters p = environment.getParameters();
        if (firstNode && p.getBoolean("rendezvous_test_makes_bootstrap")) {
            firstNode = false;
            // this just guards the next part
        } else if (p.getBoolean("rendezvous_test_firewall")) {
            if (random.nextFloat() <= p.getFloat("rendezvous_test_num_firewalled")) {
                pn.getVars().put(SIMULATE_FIREWALL, true);
                contactState = RendezvousSocketNodeHandle.CONTACT_FIREWALLED;
            }
        }

        RendezvousSNHFactory pnhf = (RendezvousSNHFactory) nhf;
        MultiInetSocketAddress proxyAddress = (MultiInetSocketAddress) pn.getVars().get(PROXY_ADDRESS);
        SocketNodeHandle ret = pnhf.getNodeHandle(proxyAddress, pn.getEnvironment().getTimeSource().currentTimeMillis(), pn.getNodeId(), contactState);

        // this code is for logging
        if (logger.isDebugEnabled() || (contactState != localContactState && logger.isInfoEnabled())) {
            switch (contactState) {
                case RendezvousSocketNodeHandle.CONTACT_DIRECT:
                    logger.info(ret + " is not firewalled.");
                    break;
                case RendezvousSocketNodeHandle.CONTACT_FIREWALLED:
                    logger.info(ret + " is firewalled.");
                    break;
            }
        }
        return ret;
    }

    /**
     * For testing, may return a FirewallTL impl for testing.
     */
    @Override
    protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, PastryNode pn) throws IOException {
        TransportLayer<InetSocketAddress, ByteBuffer> baseTl = super.getWireTransportLayer(innermostAddress, pn);
        Parameters p = pn.getEnvironment().getParameters();

        if ((pn.getVars().containsKey(SIMULATE_FIREWALL) && (Boolean) pn.getVars().get(SIMULATE_FIREWALL)) ||
                (p.contains(SIMULATE_FIREWALL) && p.getBoolean(SIMULATE_FIREWALL))) {
            return new FirewallTLImpl<>(baseTl, 5000, pn.getEnvironment());
        }

        // do the normal thing
        return baseTl;
    }

    @Override
    protected PriorityTransportLayer<MultiInetSocketAddress> getPriorityTransportLayer(
            TransportLayer<MultiInetSocketAddress, ByteBuffer> trans,
            LivenessProvider<MultiInetSocketAddress> liveness,
            ProximityProvider<MultiInetSocketAddress> prox, PastryNode pn) {
        PriorityTransportLayer<MultiInetSocketAddress> ret = super.getPriorityTransportLayer(trans, liveness, prox, pn);
        ((StandardRouter) pn.getRouter()).setRouterStrategy(new RendezvousRouterStrategy(ret, pn.getEnvironment()));
        return ret;
    }

    /**
     * This code opens a pilot to our bootstrap node before proceeding.  This is necessary to allow the liveness
     * checks to be sent back to me without the bootstrap node remembering the address that I sent the liveness
     * check on.
     * <p>
     * When the node goes live, close all of the opened pilots, to not blow out the bootstrap node.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Bootstrapper getBootstrapper(final PastryNode pn,
                                           NodeHandleAdapter tl,
                                           NodeHandleFactory handleFactory,
                                           ProximityNeighborSelector pns) {
        final PilotManager<RendezvousSocketNodeHandle> manager = (PilotManager<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_TL);

        // only do the special step if we're NATted
        if (((RendezvousSocketNodeHandle) pn.getLocalHandle()).canContactDirect())
            return super.getBootstrapper(pn, tl, handleFactory, pns);

        // set the contents true when booted
        final boolean[] booted = new boolean[1];
        booted[0] = false;
        final ArrayList<RendezvousSocketNodeHandle> closeMeWhenReady = new ArrayList<>();
        Observer obs = new Observer() {
            public void update(Observable o, Object arg) {
                if (arg instanceof Boolean) {
                    if ((Boolean) arg) {
                        // node is ready
                        List<RendezvousSocketNodeHandle> temp;
                        synchronized (closeMeWhenReady) {
                            booted[0] = true;
                        }
                        for (RendezvousSocketNodeHandle nh : closeMeWhenReady) {
                            manager.closePilot(nh);
                        }
                        closeMeWhenReady.clear();
                        o.deleteObserver(this);
                    }
                }
            }
        };
        pn.addObserver(obs);

        return new TLBootstrapper(pn, tl.getTL(), (SocketNodeHandleFactory) handleFactory, pns) {
            @Override
            protected void checkLiveness(final SocketNodeHandle h, Map<String, Object> options) {
                // open pilot first, then call checkliveness, but it's gonna fail the first time, because the NH is bogus.
                // so, open it the first time and watch it fail, then open it again
                ContactDirectStrategy<RendezvousSocketNodeHandle> contactStrat =
                        (ContactDirectStrategy<RendezvousSocketNodeHandle>) pn.getVars().get(RENDEZVOUS_CONTACT_DIRECT_STRATEGY);
                RendezvousSocketNodeHandle rsnh = (RendezvousSocketNodeHandle) h;
                if (!rsnh.canContactDirect() && contactStrat.canContactDirect(rsnh)) {
                    super.checkLiveness(h, options);
                    return;
                }

                manager.openPilot((RendezvousSocketNodeHandle) h, new Continuation<SocketRequestHandle<RendezvousSocketNodeHandle>, Exception>() {

                    public void receiveResult(
                            SocketRequestHandle<RendezvousSocketNodeHandle> result) {

                        // don't hold the lock when calling closePilot()
                        boolean close = false;

                        // see if we already joined
                        synchronized (closeMeWhenReady) {
                            if (booted[0]) {
                                close = true;
                            } else {
                                closeMeWhenReady.add(result.getIdentifier());
                            }
                        }
                        if (close) {
                            logger.error("closing pilot");
                            manager.closePilot(result.getIdentifier());
                            return;
                        }

                        // remember to close the pilot once we're joined
                        pn.getLivenessProvider().checkLiveness(h, null);
                    }

                    public void receiveException(Exception exception) {
                        logger.error("In Rendezvous Bootstrapper.checkLiveness(" + h + ")", exception);
                    }
                });
            }
        };
    }

    /**
     * @return true if ip address matches firewall prefix
     */
    protected boolean isInternetRoutablePrefix(InetAddress address) {
        String ip = address.getHostAddress();
        String nattedNetworkPrefixes = environment.getParameters().getString(
                "nat_network_prefixes");

        String[] nattedNetworkPrefix = nattedNetworkPrefixes.split(";");
        for (String networkPrefix : nattedNetworkPrefix) {
            if (ip.startsWith(networkPrefix)) {
                return false;
            }
        }
        return true;
    }
}
