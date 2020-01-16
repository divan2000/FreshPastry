package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.simple;

import java.util.Collection;

/**
 * This is a simplified Source Route Strategy, that only needs to
 * provide a next hop.
 *
 * @author Jeff Hoye
 */
public interface NextHopStrategy<Identifier> {

    /**
     * @param destination
     * @return good next candidates (random nodes that we suspect are alive are a good start)
     * to reach the destination
     */
    Collection<Identifier> getNextHops(Identifier destination);
}
