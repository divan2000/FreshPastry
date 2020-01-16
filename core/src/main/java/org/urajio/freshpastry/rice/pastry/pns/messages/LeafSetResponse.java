package org.urajio.freshpastry.rice.pastry.pns.messages;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;

public class LeafSetResponse extends PRawMessage {
    public static final short TYPE = 2;

    public LeafSet leafset;

    public LeafSetResponse(LeafSet leafset, int dest) {
        super(dest);
        this.leafset = leafset;
        setPriority(HIGH_PRIORITY);
    }

    public static org.urajio.freshpastry.rice.p2p.commonapi.Message build(InputBuffer buf, NodeHandleFactory nhf, int dest) throws IOException {
        byte version = buf.readByte();
        if (version == 0) {
            return new LeafSetResponse(LeafSet.build(buf, nhf), dest);
        }
        throw new IllegalStateException("Unknown version: " + version);
    }

    @Override
    public short getType() {
        return TYPE;
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        leafset.serialize(buf);
    }
}
