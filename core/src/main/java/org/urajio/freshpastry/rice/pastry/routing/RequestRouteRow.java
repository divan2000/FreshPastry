package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * Request a row from the routing table from another node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class RequestRouteRow extends PRawMessage implements Serializable {
    public static final short TYPE = 1;

    private short row;

    /**
     * Constructor.
     *
     * @param nh the return handle.
     * @param r  which row
     */

    public RequestRouteRow(NodeHandle nh, short r) {
        this(null, nh, r);
    }

    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param nh    the return handle
     * @param r     which row
     */
    public RequestRouteRow(Date stamp, NodeHandle nh, short r) {
        super(RouteProtocolAddress.getCode(), stamp);
        setSender(nh);
        row = r;
        setPriority(MAX_PRIORITY);
    }

    public RequestRouteRow(NodeHandle sender, InputBuffer buf) throws IOException {
        super(RouteProtocolAddress.getCode(), null);
        setSender(sender);

        byte version = buf.readByte();
        if (version == 0) {
            row = buf.readShort();
            setPriority(MAX_PRIORITY);
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

    /**
     * Gets the row that made the request.
     *
     * @return the row.
     */

    public short getRow() {
        return row;
    }

    public String toString() {
        String s = "";

        s += "RequestRouteRow(row " + row + " by " + getSender().getNodeId() + ")";

        return s;
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        buf.writeShort(row);
    }
}