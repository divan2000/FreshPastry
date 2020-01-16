package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

public interface IncomingPilotListener<HighIdentifier> {
    void pilotOpening(HighIdentifier i);

    void pilotClosed(HighIdentifier i);
}
