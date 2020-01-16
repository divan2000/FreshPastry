package org.urajio.freshpastry.rice.p2p.commonapi;

import java.io.Serializable;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * @(#) Message.java
 * <p>
 * This interface is an abstraction of a message which is sent through
 * the common API-based system.
 */
public interface Message extends Serializable {

    // different priority levels
    int MAX_PRIORITY = -15;
    int HIGH_PRIORITY = -10;
    int MEDIUM_HIGH_PRIORITY = -5;
    int MEDIUM_PRIORITY = 0;
    int MEDIUM_LOW_PRIORITY = 5;
    int LOW_PRIORITY = 10;
    int LOWEST_PRIORITY = 15;
    int DEFAULT_PRIORITY = MEDIUM_PRIORITY;

    /**
     * Method which should return the priority level of this message.  The messages
     * can range in priority from -15 (highest priority) to 15 (lowest) -
     * when sending messages across the wire, the queue is sorted by message priority.
     * If the queue reaches its limit, the lowest priority messages are discarded.  Thus,
     * applications which are very verbose should have LOW_PRIORITY or lower, and
     * applications which are somewhat quiet are allowed to have MEDIUM_PRIORITY or
     * possibly even HIGH_PRIORITY.
     *
     * @return This message's priority
     */
    int getPriority();

}


