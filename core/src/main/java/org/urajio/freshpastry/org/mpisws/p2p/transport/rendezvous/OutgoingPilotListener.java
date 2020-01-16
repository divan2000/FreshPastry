package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

/**
 * Tells a NATted node about outgoingPilots.
 *
 * @param <HighIdentifier>
 * @author Jeff Hoye
 */
public interface OutgoingPilotListener<HighIdentifier> {
    void pilotOpening(HighIdentifier i);

    void pilotClosed(HighIdentifier i);
}
