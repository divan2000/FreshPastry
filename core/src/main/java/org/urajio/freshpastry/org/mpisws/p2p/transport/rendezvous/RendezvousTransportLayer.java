package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public interface RendezvousTransportLayer<HighIdentifier> {
    int SUCCESS = 1;

    /**
     * Open a socket to the dest, then after writing credentials, call notify the higher layer: incomingSocket()
     */
    void openChannel(HighIdentifier requestor, HighIdentifier middleMan, int uid);

    /**
     * Called when a message was routed by the overlay to this node.
     *
     * @param i
     * @param m
     * @param options
     * @throws IOException
     */
    void messageReceivedFromOverlay(HighIdentifier i, ByteBuffer m, Map<String, Object> options) throws IOException;
}
