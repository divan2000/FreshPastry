package org.urajio.freshpastry.rice.pastry.join;

import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;

/**
 * Request for the join protocols on the local node to join the overlay.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class InitiateJoin extends Message implements Serializable {
    private NodeHandle[] handle;


    public InitiateJoin(Collection<NodeHandle> nh) {
        this(null, nh);
    }


    public InitiateJoin(Date stamp, Collection<NodeHandle> nh) {
        super(JoinAddress.getCode(), stamp);
        handle = nh.toArray(new NodeHandle[1]);
    }

    /**
     * Gets the handle for the join.
     * <p>
     * Gets the first non-dead handle for the join.
     *
     * @return the handle.
     */
    public NodeHandle getHandle() {
        for (NodeHandle nodeHandle : handle) {
            if (nodeHandle.isAlive()) return nodeHandle;
        }
        return null;
    }

    public String toString() {
        String s = "IJ{";
        for (int i = 0; i < handle.length; i++) {
            s += handle[i] + ":" + handle[i].isAlive();
            if (i != handle.length - 1) s += ",";
        }
        s += "}";
        return s;
    }
}

