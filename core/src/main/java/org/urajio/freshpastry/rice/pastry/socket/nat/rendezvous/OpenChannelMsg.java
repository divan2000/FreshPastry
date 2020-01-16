package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;

public class OpenChannelMsg extends PRawMessage {

    public static final short TYPE = 3;

    RendezvousSocketNodeHandle rendezvous;
    RendezvousSocketNodeHandle source;
    int uid;

    public OpenChannelMsg(int address, RendezvousSocketNodeHandle rendezvous, RendezvousSocketNodeHandle source, int uid) {
        super(address);
        this.rendezvous = rendezvous;
        this.source = source;
        this.uid = uid;
    }

    public RendezvousSocketNodeHandle getRendezvous() {
        return rendezvous;
    }

    public RendezvousSocketNodeHandle getSource() {
        return source;
    }

    public int getUid() {
        return uid;
    }

    public String toString() {
        return "OpenChannelMsg<" + uid + "> source:" + source + " rendezvous:" + rendezvous;
    }

    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        rendezvous.serialize(buf);
        source.serialize(buf);
        buf.writeInt(uid);
    }
}
