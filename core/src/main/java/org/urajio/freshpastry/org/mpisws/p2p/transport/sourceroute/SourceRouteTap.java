package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;

import java.nio.ByteBuffer;

/**
 * Notified of messages sent to intermediate nodes.
 *
 * @author Jeff Hoye
 */
public interface SourceRouteTap {
    /**
     * We are the intermediate node for a message.
     *
     * @param m
     * @param path
     */
    void receivedMessage(ByteBuffer m, SourceRoute path);

    /**
     * @param path
     * @param a
     * @param b
     */
    void socketOpened(SourceRoute path, P2PSocket a, P2PSocket b);

    /**
     * @param path
     * @param a
     * @param b
     */
    void socketClosed(SourceRoute path, P2PSocket a, P2PSocket b);

    /**
     * We are the intermediate node for some bytes from Socket a to Socket b
     *
     * @param m
     * @param path
     * @param a
     * @param b
     */
    void receivedBytes(ByteBuffer m, SourceRoute path, P2PSocket a, P2PSocket b);
}
