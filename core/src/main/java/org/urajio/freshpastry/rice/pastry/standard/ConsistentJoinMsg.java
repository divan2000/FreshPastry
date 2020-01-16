package org.urajio.freshpastry.rice.pastry.standard;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.join.JoinAddress;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.util.HashSet;

/**
 * @author Jeff Hoye
 */
public class ConsistentJoinMsg extends PRawMessage {
    public static final short TYPE = 2;
    private static final long serialVersionUID = -8942404626084999673L;
    LeafSet ls;
    boolean request;
    HashSet<NodeHandle> failed;

    /**
     *
     */
    public ConsistentJoinMsg(LeafSet ls, HashSet<NodeHandle> failed, boolean request) {
        super(JoinAddress.getCode());
        this.ls = ls;
        this.request = request;
        this.failed = failed;
    }

    public ConsistentJoinMsg(InputBuffer buf, NodeHandleFactory nhf, NodeHandle sender) throws IOException {
        super(JoinAddress.getCode());
        byte version = buf.readByte();
        if (version == 0) {
            setSender(sender);
            ls = LeafSet.build(buf, nhf);
            request = buf.readBoolean();
            failed = new HashSet<>();
            int numInSet = buf.readInt();
            for (int i = 0; i < numInSet; i++) {
                failed.add(nhf.readNodeHandle(buf));
            }
        } else {
            throw new IOException("Unknown Version: " + version);
        }
    }

    public String toString() {
        return "ConsistentJoinMsg " + ls + " request:" + request;
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        ls.serialize(buf);
        buf.writeBoolean(request);
        buf.writeInt(failed.size());
        for (NodeHandle h : failed) {
            h.serialize(buf);
        }
    }
}
