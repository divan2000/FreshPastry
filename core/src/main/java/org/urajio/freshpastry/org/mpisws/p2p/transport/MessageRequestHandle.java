package org.urajio.freshpastry.org.mpisws.p2p.transport;

import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.util.Map;

/**
 * Can cancel the request to send the message.
 *
 * @param <Identifier>
 * @param <MessageType>
 * @author Jeff Hoye
 */
public interface MessageRequestHandle<Identifier, MessageType> extends Cancellable {
    MessageType getMessage();

    Identifier getIdentifier();

    Map<String, Object> getOptions();
}
