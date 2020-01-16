package org.urajio.freshpastry.org.mpisws.p2p.transport.identity;

/**
 * Notified every time we hear of a new UpperIdentifier (usually deserialization)
 *
 * @param <UpperIdentifier>
 * @author Jeff Hoye
 */
public interface SerializerListener<UpperIdentifier> {
    void nodeHandleFound(UpperIdentifier handle);
}
