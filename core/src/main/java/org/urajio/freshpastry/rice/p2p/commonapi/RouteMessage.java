package org.urajio.freshpastry.rice.p2p.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * <p>
 * This interface is a container which represents a message, as it is
 * about to be forwarded to another node.
 */
public interface RouteMessage extends Serializable {

    /**
     * Returns the destination Id for this message
     *
     * @return The destination Id
     */
    Id getDestinationId();

    /**
     * Sets the destination Id for this message
     *
     * @param id The destination Id
     */
    void setDestinationId(Id id);

    /**
     * Returns the next hop handle for this message
     *
     * @return The next hop
     */
    NodeHandle getNextHopHandle();

    /**
     * Sets the next hop handle for this message
     *
     * @param nextHop The next hop for this handle
     */
    void setNextHopHandle(NodeHandle nextHop);

    /**
     * Returns the enclosed message inside of this message
     *
     * @return The enclosed message
     * @deprecated use getMesage(MessageDeserializer)
     */
    @Deprecated
    Message getMessage();

    /**
     * Sets the internal message for this message
     *
     * @param message The internal message
     */
    void setMessage(Message message);

    /**
     * Sets the internal message for this message
     * <p>
     * Does the same as setMessage(Message) but with better
     * performance, because it doesn't have to introspect
     * if the message is a RawMessage
     *
     * @param message The internal message
     */
    void setMessage(RawMessage message);

    Message getMessage(MessageDeserializer md) throws IOException;
}


