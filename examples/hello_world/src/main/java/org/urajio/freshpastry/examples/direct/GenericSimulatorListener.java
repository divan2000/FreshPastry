package org.urajio.freshpastry.examples.direct;

public interface GenericSimulatorListener<Identifier, MessageType> {
    /**
     * Called for every message sent over the network.
     *
     * @param m     the Message that was sent.
     * @param from  the source.
     * @param to    the destination
     * @param delay when the message will be delivered (in millis)
     */
    void messageSent(MessageType m, Identifier from, Identifier to, int delay);

    /**
     * Called for every message received over the network.
     *
     * @param m    the Message that was sent.
     * @param from the source.
     * @param to   the destination
     */
    void messageReceived(MessageType m, Identifier from, Identifier to);
}
