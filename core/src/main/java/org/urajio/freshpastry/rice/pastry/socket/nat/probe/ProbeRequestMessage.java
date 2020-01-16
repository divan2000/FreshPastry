package org.urajio.freshpastry.rice.pastry.socket.nat.probe;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;

public class ProbeRequestMessage extends PRawMessage {

    public static final short TYPE = 1;

    MultiInetSocketAddress probeRequestor;
    long uid;

    /**
     * @param probeRequestor please probe this guy
     * @param uid            use this unique identifier
     * @param appAddress     the application address
     */
    public ProbeRequestMessage(MultiInetSocketAddress probeRequestor, long uid, int appAddress) {
        super(appAddress);
        this.probeRequestor = probeRequestor;
        this.uid = uid;
    }

    public static ProbeRequestMessage build(InputBuffer buf, int appAddress) throws IOException {
        byte version = buf.readByte();
        if (version != 0) throw new IllegalStateException("Unknown version: " + version);
        MultiInetSocketAddress addr = MultiInetSocketAddress.build(buf);
        long uid = buf.readLong();
        return new ProbeRequestMessage(addr, uid, appAddress);
    }

    // *********** Getters **************
    public MultiInetSocketAddress getProbeRequester() {
        return probeRequestor;
    }

    public long getUID() {
        return uid;
    }

    // ***************** Raw Serialization ****************
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        probeRequestor.serialize(buf);
        buf.writeLong(uid);
    }
}
