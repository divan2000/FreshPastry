package org.urajio.freshpastry.org.mpisws.p2p.transport;

/**
 * Notifies TransportLayerListeners of reading/writing.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface ListenableTransportLayer<Identifier> {
    void addTransportLayerListener(TransportLayerListener<Identifier> listener);

    void removeTransportLayerListener(TransportLayerListener<Identifier> listener);
}
