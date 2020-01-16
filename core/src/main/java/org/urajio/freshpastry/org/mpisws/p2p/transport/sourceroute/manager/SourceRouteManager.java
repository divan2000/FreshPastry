package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;

import java.nio.ByteBuffer;

/**
 * The purpose of this class is to hide the detail of sourcerouting.  It adapts a
 * TransportLayer<SourceRoute, Buffer> => TransportLayer<Identifier, Buffer>
 *
 * @author Jeff Hoye
 */
public interface SourceRouteManager<Identifier> extends
        TransportLayer<Identifier, ByteBuffer>,
        LivenessProvider<Identifier>,
        ProximityProvider<Identifier> {
}
