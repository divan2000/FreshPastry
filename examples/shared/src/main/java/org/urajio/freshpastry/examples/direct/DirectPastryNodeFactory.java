package org.urajio.freshpastry.examples.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.examples.transport.direct.DirectTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSetProtocol;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.standard.ProximityNeighborSelector;
import org.urajio.freshpastry.rice.pastry.standard.StandardLeafSetProtocol;
import org.urajio.freshpastry.rice.pastry.transport.NodeHandleAdapter;
import org.urajio.freshpastry.rice.pastry.transport.TLDeserializer;
import org.urajio.freshpastry.rice.pastry.transport.TransportPastryNodeFactory;

import java.io.IOException;
import java.util.*;

/**
 * Pastry node factory for direct connections between nodes (local instances).
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 * @author Rongmei Zhang/Y. Charlie Hu
 * @version $Id$
 */
public class DirectPastryNodeFactory extends TransportPastryNodeFactory {
    private final static Logger logger = LoggerFactory.getLogger(DirectPastryNodeFactory.class);

    protected NodeIdFactory nidFactory;

    protected NetworkSimulator simulator;

    protected Collection<NodeHandleFactoryListener<NodeHandle>> listeners =
            new ArrayList<>();
    HashMap<Id, NodeRecord> recordTable = new HashMap<>();

    /**
     * Main constructor.
     *
     * @param nf  the NodeIdFactory
     * @param sim the NetworkSimulator
     * @param env the Enviornment
     */
    public DirectPastryNodeFactory(NodeIdFactory nf, NetworkSimulator<DirectNodeHandle, RawMessage> sim, Environment env) {
        super(env);
        env.getParameters().setInt("pastry_protocol_consistentJoin_max_time_to_be_scheduled", 120000);
        nidFactory = nf;
        simulator = sim;
    }

    @Override
    protected LeafSetProtocol getLeafSetProtocol(PastryNode pn, LeafSet leafSet, RoutingTable routeTable) {
        if (pn.getEnvironment().getParameters().getBoolean("pastry_direct_guarantee_consistency")) { // true
            return super.getLeafSetProtocol(pn, leafSet, routeTable);
        } else {
            StandardLeafSetProtocol ret = new StandardLeafSetProtocol(pn, pn.getLocalHandle(), leafSet, routeTable);
            ret.register();
            return ret;
        }
    }

    /**
     * Getter for the NetworkSimulator.
     *
     * @return the NetworkSimulator we are using.
     */
    public NetworkSimulator<DirectNodeHandle, RawMessage> getNetworkSimulator() {
        return simulator;
    }

    /**
     * Manufacture a new Pastry node.
     *
     * @return a new PastryNode
     */
    public PastryNode newNode(NodeHandle bootstrap) {
        return newNode(bootstrap, nidFactory.generateNodeId());
    }

    public PastryNode newNode() throws IOException {
        return newNode(nidFactory.generateNodeId());
    }

    public PastryNode newNode(NodeHandle bootstrap, Id nodeId) {
        try {
            if (bootstrap == null) {
                logger.warn("No bootstrap node provided, starting a new ring...");
            }
            PastryNode pn = newNode(nodeId);
            if (bootstrap == null) {
                pn.getBootstrapper().boot(Collections.EMPTY_LIST);
            } else {
                pn.getBootstrapper().boot(Collections.singleton(bootstrap));
            }
            return pn;
        } catch (IOException ioe) {
            logger.error("Couldn't construct node.", ioe);
            return null;
        }
    }

    /**
     * Manufacture a new Pastry node.
     *
     * @return a new PastryNode
     */
    public PastryNode newNode(Id nodeId) throws IOException {

        // this code builds a different environment for each PastryNode
        Environment environment = this.environment;
        if (this.environment.getParameters().getBoolean("pastry_factory_multipleNodes")) {
            environment = new Environment(
                    this.environment.getSelectorManager(),
                    this.environment.getProcessor(),
                    this.environment.getRandomSource(),
                    this.environment.getTimeSource(),
                    this.environment.getParameters(),
                    this.environment.getExceptionStrategy());
        }
        PastryNode pn = new PastryNode(nodeId, environment);
        nodeHandleHelper(pn);

        return pn;
    }

    /**
     * This method returns the remote leafset of the provided handle
     * to the caller, in a protocol-dependent fashion.  Note that this method
     * may block while sending the message across the wire.
     *
     * @param handle The node to connect to
     * @return The leafset of the remote node
     */
    public LeafSet getLeafSet(NodeHandle handle) {
        DirectNodeHandle dHandle = (DirectNodeHandle) handle;

        return dHandle.getRemote().getLeafSet();
    }

    public CancellableTask getLeafSet(NodeHandle handle, Continuation<LeafSet, Exception> c) {
        DirectNodeHandle dHandle = (DirectNodeHandle) handle;
        c.receiveResult(dHandle.getRemote().getLeafSet());
        return new NullCancellableTask();
    }

    /**
     * This method returns the remote route row of the provided handle
     * to the caller, in a protocol-dependent fashion.  Note that this method
     * may block while sending the message across the wire.
     *
     * @param handle The node to connect to
     * @param row    The row number to retrieve
     * @return The route row of the remote node
     */
    public RouteSet[] getRouteRow(NodeHandle handle, int row) {
        DirectNodeHandle dHandle = (DirectNodeHandle) handle;

        return dHandle.getRemote().getRoutingTable().getRow(row);
    }

    public CancellableTask getRouteRow(NodeHandle handle, int row, Continuation<RouteSet[], Exception> c) {
        DirectNodeHandle dHandle = (DirectNodeHandle) handle;
        c.receiveResult(dHandle.getRemote().getRoutingTable().getRow(row));
        return new NullCancellableTask();
    }

    /**
     * This method determines and returns the proximity of the current local
     * node the provided NodeHandle.  This will need to be done in a protocol-
     * dependent fashion and may need to be done in a special way.
     */
    public int getProximity(NodeHandle local, NodeHandle remote) {
        return (int) simulator.proximity(local, remote);
    }

    @Override
    protected NodeHandle getLocalHandle(PastryNode pn, NodeHandleFactory handleFactory) {
        return new DirectNodeHandle(pn, simulator);
    }

    @Override
    protected NodeHandleFactory getNodeHandleFactory(PastryNode pn) {
        // TODO: Make this work
        return new NodeHandleFactory<NodeHandle>() {

            public NodeHandle readNodeHandle(InputBuffer buf) {
                return null;
            }

            public NodeHandle coalesce(NodeHandle handle) {
                notifyListeners(handle);
                return null;
            }

            /**
             * Notify the listeners that this new handle has come along.
             */
            protected void notifyListeners(NodeHandle nh) {
                Collection<NodeHandleFactoryListener<NodeHandle>> temp = listeners;
                synchronized (listeners) {
                    temp = new ArrayList<>(listeners);
                }
                for (NodeHandleFactoryListener<NodeHandle> foo : temp) {
                    foo.nodeHandleFound(nh);
                }
            }

            public void addNodeHandleFactoryListener(
                    NodeHandleFactoryListener<NodeHandle> listener) {
                synchronized (listeners) {
                    listeners.add(listener);
                }
            }


            public void removeNodeHandleFactoryListener(
                    NodeHandleFactoryListener<NodeHandle> listener) {
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            }
        };
    }

    @Override
    protected NodeHandleAdapter getNodeHandleAdapter(final PastryNode pn, NodeHandleFactory handleFactory, TLDeserializer deserializer) {
        NodeRecord nr = (NodeRecord) recordTable.get(pn.getId());
        if (nr == null) {
            nr = simulator.generateNodeRecord();
            recordTable.put(pn.getNodeId(), nr);
        }
        TransportLayer<NodeHandle, RawMessage> tl = getDirectTransportLayer(pn, nr);
        // new DirectTransportLayer<NodeHandle, RawMessage>(pn.getLocalHandle(), simulator, nr, pn.getEnvironment());

        return new NodeHandleAdapter(tl, simulator.getLivenessProvider(), new ProximityProvider<NodeHandle>() {
            // proximity won't change, so don't worry about it
            List<ProximityListener<NodeHandle>> proxListeners = new ArrayList<>();

            @Override
            public int proximity(NodeHandle i, Map<String, Object> options) {
                return (int) simulator.proximity((DirectNodeHandle) pn.getLocalHandle(), (DirectNodeHandle) i);
            }

            @Override
            public void addProximityListener(ProximityListener<NodeHandle> name) {
                proxListeners.add(name);
            }

            @Override
            public boolean removeProximityListener(ProximityListener<NodeHandle> name) {
                return proxListeners.remove(name);
            }

            @Override
            public void clearState(NodeHandle i) {
            }
        });
    }

    /**
     * Override me
     *
     * @param pn
     * @param nr
     * @return
     */
    protected TransportLayer<NodeHandle, RawMessage> getDirectTransportLayer(PastryNode pn, NodeRecord nr) {
        return new DirectTransportLayer<NodeHandle, RawMessage>(pn.getLocalHandle(), simulator, nr, pn.getEnvironment());
    }

    @Override
    protected Bootstrapper getBootstrapper(final PastryNode pn, NodeHandleAdapter tl, NodeHandleFactory handleFactory, final ProximityNeighborSelector pns) {
        return new Bootstrapper<NodeHandle>() {

            public void boot(Collection<NodeHandle> bootaddresses) {
                pns.getNearHandles(bootaddresses, new Continuation<Collection<NodeHandle>, Exception>() {
                    public void receiveResult(Collection<NodeHandle> result) {
                        logger.info("boot() calling pn.doneNode(" + result + ")");
                        pn.doneNode(result);
                    }

                    public void receiveException(Exception exception) {
                        // TODO Auto-generated method stub
                    }
                });
            }
        };
    }

    /**
     * The non-blocking versions here all execute immeadiately.
     * This CancellableTask is just a placeholder.
     *
     * @author Jeff Hoye
     */
    static class NullCancellableTask implements CancellableTask {
        public void run() {
        }

        public boolean cancel() {
            return false;
        }

        public long scheduledExecutionTime() {
            return 0;
        }
    }
}
