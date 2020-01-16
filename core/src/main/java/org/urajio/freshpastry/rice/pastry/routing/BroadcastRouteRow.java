package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * Broadcast message for a row from a routing table.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class BroadcastRouteRow extends PRawMessage implements Serializable {
    public static final short TYPE = 2;
    private NodeHandle fromNode;
    private RouteSet[] row;

    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param from  the node id
     * @param r     the row
     */
    public BroadcastRouteRow(Date stamp, NodeHandle from, RouteSet[] r) {
        super(RouteProtocolAddress.getCode(), stamp);
        fromNode = from;
        row = r;
        setPriority(MAX_PRIORITY);
    }

    /**
     * Constructor.
     *
     * @param from the node id
     * @param r    the row
     */
    public BroadcastRouteRow(NodeHandle from, RouteSet[] r) {
        this(null, from, r);
    }

    public BroadcastRouteRow(InputBuffer buf, NodeHandleFactory nhf, PastryNode localNode) throws IOException {
        super(RouteProtocolAddress.getCode(), null);

        byte version = buf.readByte();
        if (version == 0) {
            fromNode = nhf.readNodeHandle(buf);
            row = new RouteSet[buf.readByte()];
            for (int i = 0; i < row.length; i++)
                if (buf.readBoolean()) {
                    row[i] = new RouteSet(buf, nhf, localNode);
                }
        } else {
            throw new IOException("Unknown Version: " + version);
        }
    }

    /**
     * Gets the from node.
     *
     * @return the from node.
     */
    public NodeHandle from() {
        return fromNode;
    }

    /**
     * Gets the row that was sent in the message.
     *
     * @return the row.
     */
    public RouteSet[] getRow() {
        return row;
    }

    @Override
    public String toString() {
        String s = "";

        s += "BroadcastRouteRow(of " + fromNode.getNodeId() + ")";

        return s;
    }

    public String toStringFull() {
        String s = "BRR{" + fromNode + "}:";
        for (RouteSet nodeHandles : row) {
            s += nodeHandles + "|";
        }
        return s;
    }

    /***************** Raw Serialization ***************************************/
    @Override
    public short getType() {
        return TYPE;
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0); // version
        fromNode.serialize(buf);
        buf.writeByte((byte) row.length);
        for (RouteSet nodeHandles : row) {
            if (nodeHandles != null) {
                buf.writeBoolean(true);
                nodeHandles.serialize(buf);
            } else {
                buf.writeBoolean(false);
            }
        }
    }
}