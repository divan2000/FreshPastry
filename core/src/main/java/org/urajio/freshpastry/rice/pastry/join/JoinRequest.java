package org.urajio.freshpastry.rice.pastry.join;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;

import java.io.IOException;
import java.util.Date;

/**
 * Request to join this network.
 *
 * @author Jeff Hoye, Andrew Ladd
 * @version $Id$
 */
public class JoinRequest extends PRawMessage {

    public static final byte HAS_HANDLE = 0x01;
    public static final byte HAS_JOIN_HANDLE = 0x02;
    public static final byte HAS_LEAFSET = 0x02;
    public static final short TYPE = 1;
    static final long serialVersionUID = 231671018732832563L;
    protected NodeHandle handle;

    protected NodeHandle joinHandle;
    protected long timestamp;
    private short rowCount;
    private RouteSet[][] rows;
    private LeafSet leafSet;
    private byte rtBaseBitLength;

    /**
     * Constructor.
     *
     * @param nh a handle of the node trying to join the network.
     */
    public JoinRequest(NodeHandle nh, byte rtBaseBitLength) {
        this(nh, null, rtBaseBitLength);
    }

    public JoinRequest(NodeHandle nh, byte rtBaseBitLength, long timestamp) {
        this(nh, null, rtBaseBitLength);
        this.timestamp = timestamp;
    }

    /**
     * Constructor.
     *
     * @param nh    a handle of the node trying to join the network.
     * @param stamp the timestamp
     */
    public JoinRequest(NodeHandle nh, Date stamp, byte rtBaseBitLength) {
        super(JoinAddress.getCode(), stamp);
        handle = nh;
        initialize(rtBaseBitLength);
        setPriority(MAX_PRIORITY);
    }

    public JoinRequest(InputBuffer buf, NodeHandleFactory nhf, NodeHandle sender, PastryNode localNode) throws IOException {
        super(JoinAddress.getCode());

        byte version = buf.readByte();
        switch (version) {
            case 1:
                timestamp = buf.readLong();
            case 0:
                setSender(sender);
                rtBaseBitLength = buf.readByte();
                initialize(rtBaseBitLength);

                handle = nhf.readNodeHandle(buf);
                if (buf.readBoolean())
                    joinHandle = nhf.readNodeHandle(buf);

                rowCount = buf.readShort();
                int numRows = Id.IdBitLength / rtBaseBitLength;
                int numCols = 1 << rtBaseBitLength;
                for (int i = 0; i < numRows; i++) {
                    RouteSet[] thisRow;
                    if (buf.readBoolean()) {
                        thisRow = new RouteSet[numCols];
                        for (int j = 0; j < numCols; j++) {
                            if (buf.readBoolean()) {
                                thisRow[j] = new RouteSet(buf, nhf, localNode);
                            } else {
                                thisRow[j] = null;
                            }
                        }
                    } else {
                        thisRow = null;
                    }
                    rows[i] = thisRow;
                }

                if (buf.readBoolean())
                    leafSet = LeafSet.build(buf, nhf);
                break;
            default:
                throw new IOException("Unknown Version: " + version);
        }
    }

    /**
     * Gets the handle of the node trying to join.
     *
     * @return the handle.
     */

    public NodeHandle getHandle() {
        return handle;
    }

    /**
     * Gets the handle of the node that accepted the join request;
     *
     * @return the handle.
     */

    public NodeHandle getJoinHandle() {
        return joinHandle;
    }

    /**
     * Gets the leafset of the node that accepted the join request;
     *
     * @return the leafset.
     */

    public LeafSet getLeafSet() {
        return leafSet;
    }

    /**
     * Returns true if the request was accepted, false if it hasn't yet.
     */

    public boolean accepted() {
        return joinHandle != null;
    }

    /**
     * Accept join request.
     *
     * @param nh the node handle that accepts the join request.
     */

    public void acceptJoin(NodeHandle nh, LeafSet ls) {
        joinHandle = nh;
        leafSet = ls;
    }

    /**
     * Returns the number of rows left to determine (in order).
     *
     * @return the number of rows left.
     */

    public int lastRow() {
        return rowCount;
    }

    /**
     * Push row.
     *
     * @param row the row to push.
     */

    public void pushRow(RouteSet[] row) {
        rows[--rowCount] = row;
    }

    /**
     * Get row.
     *
     * @param i the row to get.
     * @return the row.
     */

    public RouteSet[] getRow(int i) {
        return rows[i];
    }

    /**
     * Get the number of rows.
     *
     * @return the number of rows.
     */

    public int numRows() {
        return rows.length;
    }

    private void initialize(byte rtBaseBitLength) {
        joinHandle = null;
        this.rtBaseBitLength = rtBaseBitLength;
        rowCount = (short) (Id.IdBitLength / rtBaseBitLength);

        rows = new RouteSet[rowCount][];
    }

    public String toString() {
        return "JoinRequest(" + (handle != null ? handle.getNodeId() : null) + ","
                + (joinHandle != null ? joinHandle.getNodeId() : null) + "," + timestamp + ")";
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
//    buf.writeByte((byte)0); // version

        // version 1
        buf.writeByte((byte) 1); // version
        buf.writeLong(timestamp);

        buf.writeByte((byte) rtBaseBitLength);
        handle.serialize(buf);
        if (joinHandle != null) {
            buf.writeBoolean(true);
            joinHandle.serialize(buf);
        } else {
            buf.writeBoolean(false);
        }

        // encode the table
        buf.writeShort((short) rowCount);
        int maxIndex = Id.IdBitLength / rtBaseBitLength;
        for (int i = 0; i < maxIndex; i++) {
            RouteSet[] thisRow = rows[i];
            if (thisRow != null) {
                buf.writeBoolean(true);
                for (RouteSet nodeHandles : thisRow) {
                    if (nodeHandles != null) {
                        buf.writeBoolean(true);
                        nodeHandles.serialize(buf);
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            } else {
                buf.writeBoolean(false);
            }
        }

        if (leafSet != null) {
            buf.writeBoolean(true);
            leafSet.serialize(buf);
        } else {
            buf.writeBoolean(false);
        }

    }
}

