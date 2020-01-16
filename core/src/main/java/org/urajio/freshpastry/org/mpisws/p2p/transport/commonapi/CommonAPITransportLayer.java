package org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

/**
 * TransportLayer for the rice.p2p.commonapi.
 * <p>
 * Uses NodeHandle as Identifier
 * Serializes RawMessage
 * <p>
 * TODO: Rename to CommonAPITransportLayer
 *
 * @author Jeff Hoye
 */
public interface CommonAPITransportLayer<Identifier extends NodeHandle> extends
        TransportLayer<Identifier, RawMessage> {
}
