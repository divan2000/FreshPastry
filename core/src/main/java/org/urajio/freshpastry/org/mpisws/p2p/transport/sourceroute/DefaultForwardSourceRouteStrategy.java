package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import java.util.Map;

/**
 * Always accepts.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public class DefaultForwardSourceRouteStrategy<Identifier> implements SourceRouteForwardStrategy<Identifier> {

    @Override
    public boolean forward(Identifier nextHop, SourceRoute<Identifier> sr,
                           boolean socket, Map<String, Object> options) {
        return true;
    }
}
