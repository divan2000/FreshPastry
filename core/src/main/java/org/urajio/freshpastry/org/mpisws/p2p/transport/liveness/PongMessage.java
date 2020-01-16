package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;

/**
 * Class which represents a "ping" message sent through the
 * socket pastry system.
 *
 * @author Alan Mislove
 * @version $Id: PingMessage.java 3613 2007-02-15 14:45:14Z jstewart $
 */
public class PongMessage {

    long sentTime;

    /**
     * Constructor
     */
    public PongMessage(long sentTime) {
        this.sentTime = sentTime;
    }

    public PongMessage(InputBuffer buf) throws IOException {
        this(buf.readLong());
    }

    public String toString() {
        return "PongMessage<" + sentTime + ">";
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeLong(sentTime);
    }
}
