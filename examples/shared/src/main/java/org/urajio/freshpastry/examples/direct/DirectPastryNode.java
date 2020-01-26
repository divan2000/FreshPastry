package org.urajio.freshpastry.examples.direct;


import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.util.Hashtable;

/**
 * Direct pastry node. Subclasses PastryNode, and does about nothing else.
 *
 * @author Sitaram Iyer
 * @version $Id$
 */

public class DirectPastryNode {
    /**
     * Used for proximity calculation of DirectNodeHandle. This will probably go
     * away when we switch to a byte-level protocol.
     */
    static private Hashtable<Thread, PastryNode> currentNode = new Hashtable<>();

    /**
     * Returns the previous one.
     */
    public static synchronized PastryNode setCurrentNode(PastryNode dpn) {
        Thread current = Thread.currentThread();
        PastryNode ret = currentNode.get(current);
        if (dpn == null) {
            currentNode.remove(current);
        } else {
            currentNode.put(current, dpn);
        }
        return ret;
    }

    public static synchronized PastryNode getCurrentNode() {
        Thread current = Thread.currentThread();
        return currentNode.get(current);
    }
}

