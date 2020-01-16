package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

public interface RendezvousContact {
    /**
     * @return true if an Internet routable IP; false if NATted and no port forwarding, or other type FireWall.
     */
    boolean canContactDirect();
}
