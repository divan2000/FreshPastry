package org.urajio.freshpastry.org.mpisws.p2p.transport;

/**
 * The callback when a Message is sent from a transport layer.
 *
 * @param <MessageType>
 * @param <E>
 * @author Jeff Hoye
 */
public interface MessageCallback<Identifier, MessageType> {
    /**
     * Layer specific callback.
     *
     * @param msg the message that is being acknowledged.
     */
    void ack(MessageRequestHandle<Identifier, MessageType> msg);

    /**
     * Notification that the message can't be sent.
     *
     * @param msg    the message that can't be sent.
     * @param reason the reason it can't be sent (layer specific)
     */
    void sendFailed(MessageRequestHandle<Identifier, MessageType> msg, Exception reason);
}
