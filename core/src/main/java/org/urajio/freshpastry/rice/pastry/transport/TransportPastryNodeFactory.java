package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;
import org.urajio.freshpastry.rice.pastry.join.JoinProtocol;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSetProtocol;
import org.urajio.freshpastry.rice.pastry.messaging.MessageDispatch;
import org.urajio.freshpastry.rice.pastry.pns.PNSApplication;
import org.urajio.freshpastry.rice.pastry.routing.RouteSetProtocol;
import org.urajio.freshpastry.rice.pastry.routing.RouterStrategy;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.standard.*;

import java.io.IOException;
import java.util.Collection;

public abstract class TransportPastryNodeFactory extends PastryNodeFactory {

    /**
     * Large period (in seconds) means infrequent, 0 means never.
     */
    protected int leafSetMaintFreq;

    protected int routeSetMaintFreq;


    /**
     * Constructor.
     * <p>
     * Here is order for bind address 1) bindAddress parameter 2) if bindAddress
     * is null, then parameter: socket_bindAddress (if it exists) 3) if
     * socket_bindAddress doesn't exist, then InetAddress.getLocalHost()
     */
    public TransportPastryNodeFactory(Environment env) {
        super(env);
        Parameters params = env.getParameters();
        leafSetMaintFreq = params.getInt("pastry_leafSetMaintFreq");
        routeSetMaintFreq = params.getInt("pastry_routeSetMaintFreq");
    }

    public PastryNode nodeHandleHelper(PastryNode pn) throws IOException {
//    final Object lock = new Object();
//    final ArrayList<IOException> retException = new ArrayList<IOException>();
//    Object nhaPart1 = getnodeHandleAdapterPart1(pn);

        NodeHandleFactory handleFactory = getNodeHandleFactory(pn);
        NodeHandle localhandle = getLocalHandle(pn, handleFactory);

        TLDeserializer deserializer = getTLDeserializer(handleFactory, pn);

        MessageDispatch msgDisp = new MessageDispatch(pn, deserializer);
        RoutingTable routeTable = new RoutingTable(localhandle, rtMax, rtBase,
                pn);
        LeafSet leafSet = new LeafSet(localhandle, lSetSize, routeTable);
//            StandardRouter router = new RapidRerouter(pn, msgDisp);
        StandardRouter router = new RapidRerouter(pn, msgDisp, getRouterStrategy(pn));
        pn.setElements(localhandle, msgDisp, leafSet, routeTable, router);


        NodeHandleAdapter nha = getNodeHandleAdapter(pn, handleFactory, deserializer);

        pn.setSocketElements(leafSetMaintFreq, routeSetMaintFreq,
                nha, nha, nha, handleFactory);

        router.register();

        registerApps(pn, leafSet, routeTable, nha, handleFactory);

        return pn;
    }

    protected RouterStrategy getRouterStrategy(PastryNode pn) {
        return null; // use the default one
    }

    protected void registerApps(PastryNode pn, LeafSet leafSet, RoutingTable routeTable, NodeHandleAdapter nha, NodeHandleFactory handleFactory) {
        ProximityNeighborSelector pns = getProximityNeighborSelector(pn);

        Bootstrapper bootstrapper = getBootstrapper(pn, nha, handleFactory, pns);

        RouteSetProtocol rsProtocol = getRouteSetProtocol(pn, leafSet, routeTable);

        LeafSetProtocol lsProtocol = getLeafSetProtocol(pn, leafSet, routeTable);

        ReadyStrategy readyStrategy;
        if (lsProtocol instanceof ReadyStrategy) {
            readyStrategy = (ReadyStrategy) lsProtocol;
        } else {
            readyStrategy = pn.getDefaultReadyStrategy();
        }

        JoinProtocol jProtocol = getJoinProtocol(pn, leafSet, routeTable, readyStrategy);

        pn.setJoinProtocols(bootstrapper, jProtocol, lsProtocol, rsProtocol);
    }

    protected RouteSetProtocol getRouteSetProtocol(PastryNode pn, LeafSet leafSet, RoutingTable routeTable) {
        StandardRouteSetProtocol rsProtocol = new StandardRouteSetProtocol(pn, routeTable);
        rsProtocol.register();
        return rsProtocol;
    }

    protected LeafSetProtocol getLeafSetProtocol(PastryNode pn, LeafSet leafSet, RoutingTable routeTable) {
        PeriodicLeafSetProtocol lsProtocol = new PeriodicLeafSetProtocol(pn,
                pn.getLocalHandle(), leafSet, routeTable);
//    StandardLeafSetProtocol lsProtocol = new StandardLeafSetProtocol(pn,pn.getLocalHandle(),leafSet,routeTable);
        lsProtocol.register();
        return lsProtocol;

    }

    protected JoinProtocol getJoinProtocol(PastryNode pn, LeafSet leafSet, RoutingTable routeTable, ReadyStrategy lsProtocol) {
        ConsistentJoinProtocol jProtocol = new ConsistentJoinProtocol(pn,
                pn.getLocalHandle(), routeTable, leafSet, lsProtocol);
//    StandardJoinProtocol jProtocol = new StandardJoinProtocol(pn,pn.getLocalHandle(), routeTable, leafSet);
        jProtocol.register();
        return jProtocol;
    }

    protected TLDeserializer getTLDeserializer(NodeHandleFactory handleFactory, PastryNode pn) {
        return new TLDeserializer(handleFactory, pn.getEnvironment());
    }

    protected ProximityNeighborSelector getProximityNeighborSelector(PastryNode pn) {
        if (environment.getParameters().getBoolean("transport_use_pns")) {
            PNSApplication pns = new PNSApplication(pn);
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

    protected abstract NodeHandle getLocalHandle(PastryNode pn, NodeHandleFactory handleFactory);

    protected abstract NodeHandleAdapter getNodeHandleAdapter(
            PastryNode pn, NodeHandleFactory handleFactory, TLDeserializer deserializer) throws IOException;

    protected abstract NodeHandleFactory getNodeHandleFactory(PastryNode pn);

    protected abstract Bootstrapper getBootstrapper(
            PastryNode pn, NodeHandleAdapter tl, NodeHandleFactory handleFactory, ProximityNeighborSelector pns);
}
