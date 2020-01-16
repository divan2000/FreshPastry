package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import java.util.Map;

/**
 * Strategy that gives the opportunity to disallow forwarding of a socket or packet.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface SourceRouteForwardStrategy<Identifier> {
    boolean forward(Identifier nextHop, SourceRoute<Identifier> sr, boolean socket, Map<String, Object> options);
}
