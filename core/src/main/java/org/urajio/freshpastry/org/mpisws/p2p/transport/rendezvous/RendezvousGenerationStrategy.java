package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import java.util.Map;

/**
 * Picks a rendezvous point.  This has to be a node that can be directly contacted.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface RendezvousGenerationStrategy<Identifier> {
    Identifier getRendezvousPoint(Identifier dest, Map<String, Object> options);
}
