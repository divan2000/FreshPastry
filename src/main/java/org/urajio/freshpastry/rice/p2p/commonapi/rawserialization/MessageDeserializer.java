package org.urajio.freshpastry.rice.p2p.commonapi.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.Message;
import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;

import java.io.IOException;

/**
 * Because Pastry/Transport layer cannot know about all messge types, each app needs to
 * provide a deserializer.  Default, there is a Java Serializer
 */
public interface MessageDeserializer {
    /**
     * Typical implementation:
     * <p>
     * RawMessage ret = super.deserialize();
     * if (ret != null) return ret;
     * <p>
     * Endpoint endpoint;
     * switch(type) {
     * case 1:
     * return new MyMessage(buf, endpoint);
     * }
     *
     * @param buf      accessor to the bytes
     * @param type     the message type, defined in RawMessage.getType()
     * @param priority the priority of the message
     * @param sender   the sender of the Message (may be null if not specified).
     * @return The deserialized message.
     * @throws IOException
     */
    Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException;
}
