package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

/**
 * Used so nodes on a lan can try to contact each other directly.
 *
 * @author Jeff Hoye
 */
public interface ContactDirectStrategy<HighIdentifier> {
    boolean canContactDirect(HighIdentifier remoteNode);
}
