package org.urajio.freshpastry.rice.pastry.messaging;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;

/**
 * Represents a message from the wire that hasn't yet been deserialized.
 * This gets passed to the application to be deserialized.
 *
 * @author Jeff Hoye
 */
public interface RawMessageDelivery {
    int getAddress();

    Message deserialize(MessageDeserializer md);
}
