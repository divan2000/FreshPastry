package org.urajio.freshpastry.rice.pastry.pns.messages;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;

import java.io.IOException;

public class RouteRowResponse extends PRawMessage {

    public static final short TYPE = 4;
    public short index;
    public RouteSet[] row;

    public RouteRowResponse(NodeHandle sender, short index, RouteSet[] row, int address) {
        super(address);
        if (sender == null) throw new IllegalArgumentException("sender == null!");
        setSender(sender);
        this.index = index;
        this.row = row;
        setPriority(HIGH_PRIORITY);
    }

    public RouteRowResponse(InputBuffer buf, PastryNode localNode, NodeHandle sender, int dest) throws IOException {
        super(dest);
        byte version = buf.readByte();
        if (version == 0) {
            setSender(sender);
            index = buf.readShort();
            int numRouteSets = buf.readInt();
            row = new RouteSet[numRouteSets];
            for (int i = 0; i < numRouteSets; i++) {
                if (buf.readBoolean()) {
                    row[i] = new RouteSet(buf, localNode, localNode);
                }
            }
        } else {
            throw new IOException("Unknown Version: " + version);
        }
    }

    @Override
    public String toString() {
        return "RRresp[" + index + "]:" + getSender();
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        buf.writeShort(index);
        buf.writeInt(row.length);
        for (RouteSet nodeHandles : row) {
            if (nodeHandles == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                nodeHandles.serialize(buf);
            }
        }
    }

    @Override
    public short getType() {
        return TYPE;
    }
}
