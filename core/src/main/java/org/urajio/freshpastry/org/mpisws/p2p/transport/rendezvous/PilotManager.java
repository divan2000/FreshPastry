package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;
import org.urajio.freshpastry.rice.Continuation;

/**
 * Used by NATted nodes.
 * <p>
 * Normally this would be notified of all changes to the leafset involving non-NATted nodes.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface PilotManager<Identifier> {
    /**
     * Tells the manager to open a pilot to the Identifier
     *
     * @param i
     * @param deliverAckToMe optional
     * @return
     */
    SocketRequestHandle<Identifier> openPilot(Identifier i, Continuation<SocketRequestHandle<Identifier>, Exception> deliverAckToMe);

    /**
     * Tells the manager that the pilot to the Identifier is no longer useful
     */
    void closePilot(Identifier i);

    void addOutgoingPilotListener(OutgoingPilotListener<Identifier> listener);

    void removeOutgoingPilotListener(OutgoingPilotListener<Identifier> listener);

    void addIncomingPilotListener(IncomingPilotListener<Identifier> listener);

    void removeIncomingPilotListener(IncomingPilotListener<Identifier> listener);
}
