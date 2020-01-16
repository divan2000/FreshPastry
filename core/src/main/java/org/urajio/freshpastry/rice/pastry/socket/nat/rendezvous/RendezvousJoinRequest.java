package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.join.JoinRequest;

import java.io.IOException;

/**
 * Includes the bootstrap (or some other node who will have a pilot from the joiner.)
 *
 * @author Jeff Hoye
 */
public class RendezvousJoinRequest extends JoinRequest {
    public static final short TYPE = 4;

    /**
     * The joiner has created a pilot connection to the pilot node.
     */
    protected NodeHandle pilot;

    public RendezvousJoinRequest(NodeHandle nh, byte rtBaseBitLength,
                                 long timestamp, NodeHandle pilot) {
        super(nh, rtBaseBitLength, timestamp);
        this.pilot = pilot;
    }

    public RendezvousJoinRequest(InputBuffer buf, NodeHandleFactory nhf, NodeHandle sender, PastryNode localNode) throws IOException {
        super(buf, nhf, sender, localNode);
        pilot = nhf.readNodeHandle(buf);
    }

    public String toString() {
        return "RendezvousJoinRequest(" + (handle != null ? handle.getNodeId() : null) + ","
                + (joinHandle != null ? joinHandle.getNodeId() : null) + "," + timestamp + " pilot:" + pilot + ")";
    }

    /***************** Raw Serialization ***************************************/
    public short getType() {
        return TYPE;
    }

    public void serialize(OutputBuffer buf) throws IOException {
        super.serialize(buf);
        pilot.serialize(buf);
    }

    public NodeHandle getPilot() {
        return pilot;
    }
}
