package org.urajio.freshpastry.rice.pastry.messaging;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

import java.util.Date;

/**
 * Adapts Message to a RawMessage
 * <p>
 * Adds the "sender" to the RawMessage
 *
 * @author Jeff Hoye
 */
public abstract class PRawMessage extends Message implements RawMessage {

    public PRawMessage(int address) {
        this(address, null);
    }

    public PRawMessage(int address, Date timestamp) {
        super(address, timestamp);
    }
}
