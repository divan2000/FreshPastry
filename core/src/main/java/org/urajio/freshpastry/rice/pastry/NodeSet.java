package org.urajio.freshpastry.rice.pastry;

import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * Represents an ordered set of NodeHandles. *
 *
 * @author Peter Druschel
 * @version $Id$
 */

public class NodeSet implements NodeSetI, Serializable {

    public static final short TYPE = 1;

    static final long serialVersionUID = 4410658508346287677L;

    /**
     * of NodeHandle
     */
    private final Vector<NodeHandle> set;

    /**
     * Constructor.
     */
    public NodeSet() {
        set = new Vector<>();
    }

    /**
     * Constructor.
     */
    public NodeSet(Vector<NodeHandle> s) {
        set = s;
    }

    /**
     * Copy constructor.
     */
    public NodeSet(NodeSet o) {
        set = new Vector(o.set);
    }

    /*
     * NodeSet methods
     */

    public NodeSet(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
        short numNodes = buf.readShort();
        set = new Vector(numNodes);
        for (int i = 0; i < numNodes; i++) {
            set.add(nhf.readNodeHandle(buf));
        }
    }

    /**
     * Appends a member to the ordered set.
     *
     * @param handle the handle to put.
     * @return false if handle was already a member, true otherwise
     */

    public boolean put(NodeHandle handle) {
        if (set.contains(handle))
            return false;

        set.add(handle);
        return true;
    }

    /**
     * Method which randomizes the order of this NodeSet
     */
    public void randomize(RandomSource random) {
        for (int i = 0; i < set.size(); i++) {
            int a = random.nextInt(set.size());
            int b = random.nextInt(set.size());
            NodeHandle tmp = set.elementAt(a);
            set.setElementAt(set.elementAt(b), a);
            set.setElementAt(tmp, b);
        }
    }

    /**
     * Finds the NodeHandle associated with a NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is
     * found.
     */

    public NodeHandle get(Id nid) {
        try {
            return set.elementAt(getIndex(nid));
        } catch (Exception e) {
            return null;
        }

    }

    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle, or null if the position is out of bounds
     */

    public NodeHandle get(int i) {
        NodeHandle h;

        try {
            h = set.elementAt(i);
        } catch (Exception e) {
            return null;
        }

        return h;
    }

    /**
     * test membership using Id
     *
     * @param id the id to test
     * @return true of NodeHandle associated with id is a member, false otherwise
     */

    //    public boolean member(NodeId nid) {
    //  return (getIndex(nid) >= 0);
    //    }
    private boolean memberId(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        return (getIndexId(id) >= 0);
    }

    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(Id nid) {
        try {
            return set.remove(getIndex(nid));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the number of elements.
     *
     * @return the size.
     */

    public int size() {
        return set.size();
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     * @return the index or -1 if no such element
     */

    public int getIndex(Id nid) {
        return getIndexId(nid);
    }

    public int getIndex(NodeHandle nh) {
        NodeHandle h;

        for (int i = 0; i < set.size(); i++) {
            try {
                h = set.elementAt(i);
                if (h.equals(nh))
                    return i;
            } catch (Exception e) {
            }
        }

        return -1;
    }

    /*
     * additional methods unique to NodeSet
     */

    private int getIndexId(org.urajio.freshpastry.rice.p2p.commonapi.Id nid) {
        NodeHandle h;

        for (int i = 0; i < set.size(); i++) {
            try {
                h = set.elementAt(i);
                if (h.getNodeId().equals(nid))
                    return i;
            } catch (Exception e) {
            }
        }

        return -1;
    }

    /**
     * insert a member at the given index
     *
     * @param index  the position at which to insert the element into the ordered
     *               set
     * @param handle the handle to add
     * @return false if handle was already a member, true otherwise
     */
    public boolean insert(int index, NodeHandle handle) {
        if (set.contains(handle))
            return false;

        set.add(index, handle);
        return true;
    }

    /**
     * remove a member
     *
     * @param handle the handle to remove
     */
    public NodeHandle remove(NodeHandle handle) {
        if (set.remove(handle)) {
            return handle;
        }
        return null;
    }

    /**
     * remove a member at a given position
     *
     * @param index the position of the member to remove
     */
    public void remove(int index) {
        set.remove(index);
    }

    /**
     * determine rank of a member
     *
     * @param handle the handle to test
     * @return rank (index) of the handle, or -1 if handle is not a member
     */
    public int indexOf(NodeHandle handle) {
        return set.indexOf(handle);
    }

    /**
     * test membership
     *
     * @param handle the handle to test
     * @return true of handle is a member, false otherwise
     */
    public boolean member(NodeHandle handle) {
        return set.contains(handle);
    }

    /**
     * return a subset of this set, consisting of the members within a given range
     * of positions
     *
     * @param from the lower end of the range (inclusive)
     * @param to   the upper end of the range (exclusive)
     * @return the subset, or null of the arguments were out of bounds
     */
    NodeSet subSet(int from, int to) {
        NodeSet res;

        try {
            res = new NodeSet(new Vector(set.subList(from, to)));
        } catch (Exception e) {
            return null;
        }

        return res;
    }

    /**
     * return an iterator that iterates over the elements of this set
     *
     * @return the iterator
     */
    public Iterator<NodeHandle> getIterator() {
        return set.iterator();
    }

    // Common API Support

    /**
     * Returns a string representation of the NodeSet
     */

    public String toString() {
        String s = "NodeSet: ";
        for (int i = 0; i < size(); i++)
            s = s + get(i).getNodeId();

        return s;
    }

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     * @return true if the put succeeded, false otherwise.
     */
    public boolean putHandle(org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle handle) {
        return put((NodeHandle) handle);
    }

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param id a node id.
     * @return the handle associated with that id or null if no such handle is
     * found.
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle getHandle(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        synchronized (set) {
            for (NodeHandle nh : set) {
                if (nh.getId().equals(id)) return nh;
            }
        }
        return null;
    }

    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle associated with that id or null if no such handle is
     * found.
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle getHandle(int i) {
        return get(i);
    }

    /**
     * Verifies if the set contains this particular id.
     *
     * @param id a node id.
     * @return true if that node id is in the set, false otherwise.
     */
    public boolean memberHandle(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        return memberId(id);
    }

    /**
     * Removes a node id and its handle from the set.
     *
     * @param id the node to remove.
     * @return the node handle removed or null if nothing.
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle removeHandle(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        return remove((org.urajio.freshpastry.rice.pastry.Id) id);
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param id the id.
     * @return the index or throws a NoSuchElementException.
     */
    public int getIndexHandle(org.urajio.freshpastry.rice.p2p.commonapi.Id id)
            throws NoSuchElementException {
        return getIndex((Id) id);
    }

    public Iterator<NodeHandle> iterator() {
        return set.iterator();
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeShort((short) set.size());
        for (NodeHandle nh : set) {
            nh.serialize(buf);
        }
    }

    public short getType() {
        return TYPE;
    }

    public Collection<NodeHandle> getCollection() {
        return set;
    }
}

