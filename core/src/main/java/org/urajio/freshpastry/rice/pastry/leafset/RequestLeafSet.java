package org.urajio.freshpastry.rice.pastry.leafset;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * Request a leaf set from another node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class RequestLeafSet extends PRawMessage implements Serializable {
    public static final short TYPE = 1;

    long timeStamp;

    /**
     * Constructor.
     *
     * @param nh the return handle.
     */

    public RequestLeafSet(NodeHandle nh, long timeStamp) {
        this(null, nh, timeStamp);
    }

    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param nh    the return handle
     */

    public RequestLeafSet(Date stamp, NodeHandle nh, long timeStamp) {
        super(LeafSetProtocolAddress.getCode(), stamp);
        setSender(nh);
        this.timeStamp = timeStamp;
        setPriority(MAX_PRIORITY);
    }

    public RequestLeafSet(NodeHandle sender, InputBuffer buf) throws IOException {
        super(LeafSetProtocolAddress.getCode());

        setSender(sender);

        byte version = buf.readByte();
        if (version == 0) {
            timeStamp = buf.readLong();
        } else {
            throw new IOException("Unknown Version: " + version);
        }
    }

    /**
     * The return handle for the message
     *
     * @return the node handle
     */

    public NodeHandle returnHandle() {
        return getSender();
    }

    public String toString() {
        String s = "";

        s += "RequestLeafSet(" + getSender() + "," + timeStamp + ")";

        return s;
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        buf.writeLong(timeStamp);
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}