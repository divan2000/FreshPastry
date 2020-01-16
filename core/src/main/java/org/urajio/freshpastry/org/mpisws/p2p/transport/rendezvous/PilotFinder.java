package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

/**
 * This interface suggests the location of a PilotSocket to contact a NATted node.
 * <p>
 * Normal steps to open socket:
 * a) canContactDirect?  normal
 * b) findPilot? open to the pilot
 * c) route the request, include local pilot
 *
 * @author Jeff Hoye
 */
public interface PilotFinder<HighIdentifier> {
    /**
     * Return null if there isn't one.
     *
     * @param i
     * @return
     */
    HighIdentifier findPilot(HighIdentifier i);
}
