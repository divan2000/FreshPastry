package org.urajio.freshpastry.rice.pastry;

/**
 * An interface to any object capable of generating nodeIds.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public interface NodeIdFactory {
    /**
     * Generates a nodeId.
     *
     * @return a new node id.
     */

    Id generateNodeId();
}
