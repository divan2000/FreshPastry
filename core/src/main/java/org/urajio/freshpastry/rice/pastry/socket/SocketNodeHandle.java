package org.urajio.freshpastry.rice.pastry.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.dist.DistNodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

public class SocketNodeHandle extends DistNodeHandle<MultiInetSocketAddress> {
    private final static Logger logger = LoggerFactory.getLogger(SocketNodeHandle.class);

    public MultiInetSocketAddress eaddress;
    protected long epoch;

    protected SocketNodeHandle(MultiInetSocketAddress eisa, long epoch, Id id, PastryNode node) {
        super(id);
        this.eaddress = eisa;
        this.epoch = epoch;
        setLocalNode(node);
    }

    /**
     * Note, this SNH needs to be coalesced!!!
     *
     * @param buf
     * @return
     */
    static SocketNodeHandle build(InputBuffer buf, PastryNode local) throws IOException {
//    NodeHandle (Version 0)
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    + Epoch InetSocketAddress                                       +
//    +                                                               +
//    +                                                               +
//    +                                                               +
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    +   Id                                                          +
//    +                                                               +
//    +                                                               +
//    +                                                               +
//    +                                                               +
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        MultiInetSocketAddress eaddr = MultiInetSocketAddress.build(buf);
        long epoch = buf.readLong();
        Id nid = Id.build(buf);
        return new SocketNodeHandle(eaddr, epoch, nid, local);
    }

    public long getEpoch() {
        return epoch;
    }

    public void setLocalNode(PastryNode pn) {
        localnode = pn;
    }

    public MultiInetSocketAddress getIdentifier() {
        return eaddress;
    }

    /**
     * Returns the last known liveness information about the Pastry node
     * associated with this handle. Invoking this method does not cause network
     * activity.
     *
     * @return true if the node is alive, false otherwise.
     */
    public int getLiveness() {
        if (isLocal()) {
            return LIVENESS_ALIVE;
        } else {
            return ((PastryNode) localnode).getLivenessProvider().getLiveness(this, null);
        }
    }

    public MultiInetSocketAddress getAddress() {
        return eaddress;
    }

    public InetSocketAddress getInetSocketAddress() {
        return eaddress.getAddress(0);
    }

    /**
     * Method which FORCES a check of liveness of the remote node.  Note that
     * this method should ONLY be called by internal Pastry maintenance algorithms -
     * this is NOT to be used by applications.  Doing so will likely cause a
     * blowup of liveness traffic.
     *
     * @return true if node is currently alive.
     */
    public boolean checkLiveness() {
        logger.debug(this + ".checkLiveness()");
        localnode.getLivenessProvider().checkLiveness(this, null);
        return isAlive();
    }

    /**
     * Method which returns whether or not this node handle is on its
     * home node.
     *
     * @return Whether or not this handle is local
     */
    public boolean isLocal() {
        assertLocalNode();
        return getLocalNode().getLocalHandle().equals(this);
    }

    /**
     * Called to send a message to the node corresponding to this handle.
     *
     * @param msg Message to be delivered, may or may not be routeMessage.
     * @deprecated use PastryNode.send(msg, nh)
     */
    public void receiveMessage(Message msg) {
        assertLocalNode();
        Map<String, Object> options = new HashMap<>(1);
        options.put(PriorityTransportLayer.OPTION_PRIORITY, msg.getPriority());
        getLocalNode().send(this, msg, null, options);
    }

    /**
     * Returns a String representation of this DistNodeHandle. This method is
     * designed to be called by clients using the node handle, and is provided in
     * order to ensure that the right node handle is being talked to.
     *
     * @return A String representation of the node handle.
     */
    public String toString() {
        return "[SNH: " + nodeId + "/" + eaddress + "]";
    }

    public String toStringFull() {
        return "[SNH: " + nodeId + "/" + eaddress + " " + epoch + "]";
    }

    /**
     * Equivalence relation for nodehandles. They are equal if and only if their
     * corresponding NodeIds are equal.
     *
     * @param obj the other nodehandle .
     * @return true if they are equal, false otherwise.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof SocketNodeHandle))
            return false;

        SocketNodeHandle other = (SocketNodeHandle) obj;

        return (epoch == other.epoch && other.getNodeId().equals(getNodeId()) && other.eaddress.equals(eaddress));
    }

    /**
     * Hash codes for node handles. It is the hashcode of their corresponding
     * NodeId's.
     *
     * @return a hash code.
     */
    public int hashCode() {
        return ((int) epoch) ^ getNodeId().hashCode() ^ eaddress.hashCode();
    }

    /**
     * Returns the last known proximity information about the Pastry node
     * associated with this handle. Invoking this method does not cause network
     * activity. Smaller values imply greater proximity. The exact nature and
     * interpretation of the proximity metric implementation-specific.
     *
     * @return the proximity metric value
     * @deprecated use PastryNode.proximity(nh)
     */
    public int proximity() {
        return ((PastryNode) localnode).getProxProvider().proximity(this, null);
    }

    /**
     * Ping the node. Refreshes the cached liveness status and proximity value of
     * the Pastry node associated with this. Invoking this method causes network
     * activity.
     *
     * @return true if node is currently alive.
     */
    public boolean ping() {
        if (localnode.getLocalHandle().equals(this)) return false;
        localnode.getLivenessProvider().checkLiveness(this, null);
        return isAlive();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param o   DESCRIBE THE PARAMETER
     * @param obj DESCRIBE THE PARAMETER
     */
    public void update(Observable o, Object obj) {
    }

    public void serialize(OutputBuffer buf) throws IOException {
        eaddress.serialize(buf);
        buf.writeLong(epoch);
        nodeId.serialize(buf);
    }
}
