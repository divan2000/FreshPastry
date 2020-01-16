package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Used on non-Firewalled nodes to predict if a firewalled node will accept a message.
 * <p>
 * For example, the firewalled node pinged the local node, now can the local node respond?  In most firewalls,
 * the local node can respond for some amount of time.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface ResponseStrategy<Identifier> {

    /**
     * True if we believe the firewall will accept the message (due to a recent message from the node)
     *
     * @param i
     * @return
     */
    boolean sendDirect(Identifier i, ByteBuffer msg, Map<String, Object> options);

    /**
     * Called when a message is directly sent to the Identifier
     *
     * @param i
     * @param msg
     * @param options
     */
    void messageSent(Identifier i, ByteBuffer msg, Map<String, Object> options);

    /**
     * Called when a message is directly received from the Identifier
     *
     * @param i
     * @param msg
     * @param options
     */
    void messageReceived(Identifier i, ByteBuffer msg, Map<String, Object> options);
}
