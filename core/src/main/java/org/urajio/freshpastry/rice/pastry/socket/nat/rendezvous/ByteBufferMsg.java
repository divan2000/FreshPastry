package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Used to hold a UDP message (usually liveness) when delivering to a firewalled node via Routing.
 *
 * @author Jeff Hoye
 */
public class ByteBufferMsg extends PRawMessage {
    public static final short TYPE = 1;

    NodeHandle originalSender;
    ByteBuffer buffer;

    public ByteBufferMsg(ByteBuffer buf, NodeHandle sender, int priority, int dest) {
        super(dest);
        this.buffer = buf;
        setPriority(priority);
        if (sender == null) throw new IllegalArgumentException("Sender == null");
        originalSender = sender;
    }

    public String toString() {
        return "BBM[" + buffer + "] from " + originalSender;
    }

    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        originalSender.serialize(buf);
        buf.writeInt(buffer.remaining());
        buf.write(buffer.array(), buffer.position(), buffer.remaining());
    }

    public NodeHandle getOriginalSender() {
        return originalSender;
    }
}
