package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager;

import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;

import java.util.Collection;

/**
 * @author Jeff Hoye
 */
public interface SourceRouteStrategy<Identifier> {

    /**
     * Do not include the destination in the list.
     *
     * @param destination
     * @return a collection of paths to the destination.  Don't include the local node at the beginning
     * of the path, nor the destination at the end.
     */
    Collection<SourceRoute<Identifier>> getSourceRoutes(Identifier destination);
}
