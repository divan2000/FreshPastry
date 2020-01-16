package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import java.nio.ByteBuffer;
import java.util.Map;

public class MessageInfoImpl implements MessageInfo {

    private ByteBuffer message;
    private Map<String, Object> options;
    private int priority;

    public MessageInfoImpl(ByteBuffer message, Map<String, Object> options,
                           int priority) {
        super();
        this.message = message;
        this.options = options;
        this.priority = priority;
    }

    public ByteBuffer getMessage() {
        return message;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public int getPriroity() {
        return priority;
    }

    public int getSize() {
        // the 4 is the size header
        return message.remaining() + 4;
    }
}
