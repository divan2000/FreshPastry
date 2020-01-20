package org.urajio.freshpastry.examples.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.SizeCheckOutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.socket.TransportLayerNodeHandle;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * the node handle used with the direct network
 *
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y. Charlie Hu
 * @version $Id$
 */

public class DirectNodeHandle extends TransportLayerNodeHandle<NodeRecord> implements Observer {
    private final static transient Logger logger = LoggerFactory.getLogger(DirectNodeHandle.class);

    //  private transient PastryNode remoteNode;
    public transient NetworkSimulator simulator;

    /**
     * Constructor for DirectNodeHandle.
     *
     * @param ln  The local pastry node
     * @param sim The current network simulator
     */
    protected DirectNodeHandle(PastryNode ln, NetworkSimulator sim) {
        localnode = ln;
        simulator = sim;

        ln.addObserver(this);
    }

    /**
     * Gets the Remote attribute of the DirectNodeHandle object
     *
     * @return The Remote value
     */
    public PastryNode getRemote() {
        return localnode;
    }

    /**
     * Gets the NodeId attribute of the DirectNodeHandle object
     *
     * @return The NodeId value
     */
    public Id getNodeId() {
        return localnode.getNodeId();
    }

    /**
     * Gets the Alive attribute of the DirectNodeHandle object
     *
     * @return The Alive value
     */
    public int getLiveness() {
        if (simulator.isAlive(this)) {
            return LIVENESS_ALIVE;
        }
        return LIVENESS_DEAD;
    }

    /**
     * Gets the Simulator attribute of the DirectNodeHandle object
     *
     * @return The Simulator value
     */
    public NetworkSimulator getSimulator() {
        return simulator;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param arg DESCRIBE THE PARAMETER
     */
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean ping() {
        return isAlive();
    }

    public final void assertLocalNode() {
        if (DirectPastryNode.getCurrentNode() == null) {
//      ctor.printStackTrace();
            throw new RuntimeException("PANIC: localnode is null in " + this + "@" + System.identityHashCode(this));
        }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     * @deprecated
     */
    public int proximity() {
        assertLocalNode();
        return getLocalNode().proximity(this);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param msg DESCRIBE THE PARAMETER
     * @deprecated use PastryNode.send()
     */
    public void receiveMessage(Message msg) {
        DirectPastryNode.getCurrentNode().send(this, msg, null, null);
    }

    /**
     * Equivalence relation for nodehandles. They are equal if and only if their corresponding NodeIds
     * are equal.
     *
     * @param obj the other nodehandle .
     * @return true if they are equal, false otherwise.
     */
    public boolean equals(Object obj) {
        // we know that there is only one of these per node in the simulator
        return obj == this;
    }

    /**
     * Hash codes for node handles.It is the hashcode of their corresponding NodeId's.
     *
     * @return a hash code.
     */
    public int hashCode() {
        return this.getNodeId().hashCode();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
        return "[DNH " + getNodeId() + "]";
    }

    /**
     * Only notify if dead.  Note that this is limitied in that it's not possible
     * to simulate a byzantine failure of a node.  But that's out of the scope of
     * the simulator.  If we leave in the first arg, the node notifies DECLARED_LIVE
     * way too often.
     */
    public void update(Observable arg0, Object arg1) {
        if (simulator.isAlive(this)) {
        } else {
            notifyObservers(NodeHandle.DECLARED_DEAD);
        }
    }

    public void serialize(OutputBuffer buf) throws IOException {
        if (buf instanceof SizeCheckOutputBuffer) {
            ((SizeCheckOutputBuffer) buf).writeSpecial(this);
        } else {
            throw new RuntimeException("DirectNodeHandle.serialize() Should not be called.  If you are doing this to determine the size, please use a SizeCheckOutputBuffer such as the DirectSizeChecker.");
        }
    }

    public NodeRecord getAddress() {
        return simulator.getNodeRecord(this);
    }

    public long getEpoch() {
        return 0;
    }
}
