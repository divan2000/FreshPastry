package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.IdentityImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;
import org.urajio.freshpastry.rice.pastry.routing.RouterStrategy;

import java.util.Iterator;
import java.util.Map;

public class RendezvousRouterStrategy implements RouterStrategy {
    private final static Logger logger = LoggerFactory.getLogger(RendezvousRouterStrategy.class);

    PriorityTransportLayer<MultiInetSocketAddress> priority;
    Environment environment;

    public RendezvousRouterStrategy(PriorityTransportLayer<MultiInetSocketAddress> priority, Environment env) {
        this.priority = priority;
        this.environment = env;
    }

    public NodeHandle pickNextHop(RouteMessage msg, Iterator<NodeHandle> i) {
        if (!i.hasNext()) return null;
        NodeHandle best = i.next();
        int bestRating = routingQuality(best);
        logger.debug("Routing " + msg + "(0) " + best + ":" + bestRating);
        if (bestRating == 0) return best;

        int ctr = 1;
        while (i.hasNext()) {
            NodeHandle next = i.next();
            int nextRating = routingQuality(next);
            logger.debug("Routing " + msg + "(" + (ctr++) + ") " + next + ":" + nextRating);

            // found a perfect node?
            if (nextRating == 0) return next;

            // found a better node?
            if (nextRating < bestRating) {
                best = next;
                bestRating = nextRating;
            }

        }

        if (bestRating > 3) {
            logger.info("Can't find route for " + msg);
            
      /*
         This is needed to prevent problems before the leafset is built, or in the case that the entire leafset is not connectable.
         Just fail if there is no chance.
       */
            return null; // fail if node is unreachable
        }

        logger.debug("Routing " + msg + "returning " + best + ":" + bestRating);
        return best;
    }

    /**
     * Returns the quality of the nh for routing 0 is optimal
     * <p>
     * 0 if connected and alive
     * Suspected is a 1 (if connected or directly connectable)
     * Alive and connected/directly contactable = 0
     * not directly connectable = 5 (unless connected)
     * 10 if faulty
     *
     * @param nh
     * @return
     */
    protected int routingQuality(NodeHandle nh) {
        RendezvousSocketNodeHandle rnh = (RendezvousSocketNodeHandle) nh;
        if (!nh.isAlive()) {
            return 10;
        }
        int connectionStatus = priority.connectionStatus(rnh.eaddress);
        int liveness = nh.getLiveness();
        boolean contactDirect = rnh.canContactDirect();

        if (contactDirect) {
            // this code biases connected nodes
            int ret = 2;

            // a point for being not suspected
            if (liveness == NodeHandle.LIVENESS_ALIVE) ret--;
            // a point for being connected
            if (connectionStatus == PriorityTransportLayer.STATUS_CONNECTED) {
                ret--;
            } else {
                priority.openPrimaryConnection(rnh.eaddress, getOptions(rnh)); // TODO: make proper options
            }
            return ret;

        }

        // !contactDirect
        if (connectionStatus > PriorityTransportLayer.STATUS_CONNECTING) {
            // connect if we can
            priority.openPrimaryConnection(rnh.eaddress, getOptions(rnh)); // TODO: make proper options
        }

        if (connectionStatus == PriorityTransportLayer.STATUS_CONNECTED) {
            if (liveness == NodeHandle.LIVENESS_ALIVE) return 0;
            return 1; // suspected
        }

        // !contactDirect, !connected, don't really care about suspected or not
        return 5;
    }

    protected Map<String, Object> getOptions(NodeHandle nh) {
        return OptionsFactory.addOption(null, IdentityImpl.NODE_HANDLE_FROM_INDEX, nh);
    }
}
