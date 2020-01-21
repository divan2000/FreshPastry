package org.urajio.freshpastry.rice.pastry.messaging;

import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.io.Serializable;
import java.util.Date;

/**
 * This is an abstract implementation of a message object.
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 * @version $Id$
 */
public abstract class Message implements Serializable, org.urajio.freshpastry.rice.p2p.commonapi.Message {
    private static final int DEFAULT_PRIORITY_LEVEL = org.urajio.freshpastry.rice.p2p.commonapi.Message.MEDIUM_PRIORITY;
    private static final long serialVersionUID = 8921944904321235696L;
    private int destination;

    private NodeHandle sender;

    /**
     * Potentially needed for reverse compatability with serialization?
     * Remove this when move to byte-level protocol.
     */
    private boolean priority;

    private int priorityLevel = DEFAULT_PRIORITY_LEVEL;

    private transient Date theStamp;

    /**
     * Constructor.
     *
     * @param dest the destination.
     */
    public Message(int dest) {
        this(dest, null);
    }

    /**
     * Constructor.
     *
     * @param dest      the destination.
     * @param timestamp the timestamp
     */
    public Message(int dest, Date timestamp) {
        if (dest == 0) throw new IllegalArgumentException("dest must != 0");
        destination = dest;
        this.theStamp = timestamp;
        sender = null;
        priority = false;
    }

    /**
     * Gets the address of message receiver that the message is for.
     *
     * @return the destination id.
     */
    public int getDestination() {
        return destination;
    }

    /**
     * Gets the timestamp of the message, if it exists.
     *
     * @return a timestamp or null if the sender did not specify one.
     */
    public Date getDate() {
        return theStamp;
    }

    /**
     * Get sender Id.
     *
     * @return the immediate sender's NodeId.
     */
    public Id getSenderId() {
        if (sender == null)
            return null;
        return sender.getNodeId();
    }

    /**
     * Get sender.
     *
     * @return the immediate sender's NodeId.
     */
    public NodeHandle getSender() {
        return sender;
    }

    /**
     * Set sender Id. Called by NodeHandle just before dispatch, so that this Id
     * is guaranteed to belong to the immediate sender.
     *
     */
    public void setSender(NodeHandle nh) {
        sender = nh;
    }

    /**
     * Get priority
     *
     * @return the priority of this message.
     */
    public int getPriority() {
        return priorityLevel;
    }

    /**
     * Set priority.
     *
     * @param prio the new priority.
     */
    protected void setPriority(int prio) {
        priorityLevel = prio;
    }

    /**
     * If the message has no timestamp, this will stamp the message.
     *
     * @param time the timestamp.
     * @return true if the message was stamped, false if the message already had a timestamp.
     */
    public boolean stamp(Date time) {
        if (theStamp == null) {
            theStamp = time;
            return true;
        } else
            return false;
    }
}
