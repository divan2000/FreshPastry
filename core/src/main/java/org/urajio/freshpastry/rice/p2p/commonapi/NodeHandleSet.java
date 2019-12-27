package org.urajio.freshpastry.rice.p2p.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.io.Serializable;
import java.util.NoSuchElementException;

/**
 * @author Jeff Hoye
 * @author Alan Mislove
 * @version $Id$
 * @(#) NodeHandleSet.java
 * <p>
 * An interface to a generic set of node handles.
 */
public interface NodeHandleSet extends Serializable /*, Iterable<NodeHandle>*/ {

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     * @return true if the put succeeded, false otherwise.
     */
    boolean putHandle(NodeHandle handle);

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param id a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    NodeHandle getHandle(Id id);

    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle associated with that id or null if no such handle is found.
     */
    NodeHandle getHandle(int i);

    /**
     * Verifies if the set contains this particular id.
     *
     * @param id a node id.
     * @return true if that node id is in the set, false otherwise.
     */
    boolean memberHandle(Id id);

    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the node handle removed or null if nothing.
     */
    NodeHandle removeHandle(Id id);

    /**
     * Gets the size of the set.
     *
     * @return the size.
     */
    int size();

    /**
     * Gets the index of the element with the given node id.
     *
     * @param id the id.
     * @return the index or throws a NoSuchElementException.
     */
    int getIndexHandle(Id id) throws NoSuchElementException;

    void serialize(OutputBuffer buf) throws IOException;

    short getType();
}
