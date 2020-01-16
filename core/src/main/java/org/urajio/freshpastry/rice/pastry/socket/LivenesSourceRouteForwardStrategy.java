package org.urajio.freshpastry.rice.pastry.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRouteForwardStrategy;

import java.util.Map;

public class LivenesSourceRouteForwardStrategy<Identifier> implements SourceRouteForwardStrategy<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(LivenesSourceRouteForwardStrategy.class);

    LivenessProvider<SourceRoute<Identifier>> liveness;
    private SourceRouteFactory<Identifier> factory;

    public LivenesSourceRouteForwardStrategy(SourceRouteFactory<Identifier> factory) {
        this.factory = factory;
    }

    public void setLivenessProvider(LivenessProvider<SourceRoute<Identifier>> liveness) {
        this.liveness = liveness;
    }

    @Override
    public boolean forward(Identifier nextHop, SourceRoute<Identifier> sr, boolean socket, Map<String, Object> options) {
        if (!socket) {
            return true;
        }
        SourceRoute<Identifier> i = factory.getSourceRoute(nextHop);
        boolean ret = (liveness.getLiveness(i, options) < LivenessProvider.LIVENESS_DEAD);
        if (!ret) {
            logger.warn("Not forwarding socket to " + nextHop + " sr:" + sr + " because I believe it is dead.");
        }
        return ret;
    }
}
