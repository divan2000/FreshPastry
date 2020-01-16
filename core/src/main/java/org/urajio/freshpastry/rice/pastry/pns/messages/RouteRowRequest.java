package org.urajio.freshpastry.rice.pastry.pns.messages;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;

public class RouteRowRequest extends PRawMessage {
    public static final short TYPE = 3;
    public short index;

    public RouteRowRequest(NodeHandle nodeHandle, short index, int dest) {
        super(dest);
        if (nodeHandle == null) throw new IllegalArgumentException("nodeHandle == null!");
        setSender(nodeHandle);
        this.index = index;
        setPriority(HIGH_PRIORITY);
    }

    public static org.urajio.freshpastry.rice.p2p.commonapi.Message build(InputBuffer buf, NodeHandle sender, int dest) throws IOException {
        byte version = buf.readByte();
        if (version == 0)
            return new RouteRowRequest(sender, buf.readShort(), dest);
        throw new IllegalStateException("Unknown version: " + version);
    }

    @Override
    public String toString() {
        return "RRReq[" + index + "]";
    }

    @Override
    public short getType() {
        return TYPE;
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        buf.writeShort(index);
    }
}
