package org.urajio.freshpastry.rice.pastry.leafset;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.util.Date;

/**
 * Broadcast a leaf set to another node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class BroadcastLeafSet extends PRawMessage {
    public static final short TYPE = 2;

    public static final int Update = 0;

    public static final int JoinInitial = 1;

    public static final int JoinAdvertise = 2;

    public static final int Correction = 3;

    private NodeHandle fromNode;

    private LeafSet theLeafSet;

    private int theType;

    private long requestTimeStamp;

    /**
     * Constructor.
     */

    public BroadcastLeafSet(NodeHandle from, LeafSet leafSet, int type, long requestTimeStamp) {
        this(null, from, leafSet, type, requestTimeStamp);
    }

    /**
     * Constructor.
     *
     * @param stamp the timestamp
     */

    public BroadcastLeafSet(Date stamp, NodeHandle from, LeafSet leafSet, int type, long requestTimeStamp) {
        super(LeafSetProtocolAddress.getCode(), stamp);

        if (leafSet == null) throw new IllegalArgumentException("Leafset is null");

        fromNode = from;
        theLeafSet = leafSet.copy();
        theType = type;
        this.requestTimeStamp = requestTimeStamp;
        setPriority(MAX_PRIORITY);
    }

    public BroadcastLeafSet(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
        super(LeafSetProtocolAddress.getCode());

        byte version = buf.readByte();
        if (version == 0) {
            fromNode = nhf.readNodeHandle(buf);
            theLeafSet = LeafSet.build(buf, nhf);
            theType = buf.readByte();
            requestTimeStamp = buf.readLong();
        } else {
            throw new IOException("Unknown Version: " + version);
        }
    }

    /**
     * Returns the node id of the node that broadcast its leaf set.
     *
     * @return the node id.
     */

    public NodeHandle from() {
        return fromNode;
    }

    /**
     * Returns the leaf set that was broadcast.
     *
     * @return the leaf set.
     */

    public LeafSet leafSet() {
        return theLeafSet;
    }

    /**
     * Returns the type of leaf set.
     *
     * @return the type.
     */

    public int type() {
        return theType;
    }

    public String toString() {
        return "BroadcastLeafSet(" + theLeafSet + "," + requestTimeStamp + ")";
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        fromNode.serialize(buf);
        theLeafSet.serialize(buf);
        buf.writeByte((byte) theType);
        buf.writeLong(requestTimeStamp);
    }

    public long getTimeStamp() {
        return requestTimeStamp;
    }
}