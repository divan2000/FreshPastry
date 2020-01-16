package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;

public class PilotForwardMsg extends PRawMessage {

    public static final short TYPE = 2;

    protected ByteBufferMsg msg;
    protected RendezvousSocketNodeHandle target;

    public PilotForwardMsg(int address, ByteBufferMsg msg, RendezvousSocketNodeHandle target) {
        super(address);
        this.msg = msg;
        this.target = target;
    }

    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        target.serialize(buf);
        msg.serialize(buf);
    }

    public ByteBufferMsg getBBMsg() {
        return msg;
    }

    public RendezvousSocketNodeHandle getTarget() {
        return target;
    }

    public String toString() {
        return "PFM{" + msg + "->" + target + "}";
    }
}
