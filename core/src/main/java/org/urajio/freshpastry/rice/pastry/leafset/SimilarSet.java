package org.urajio.freshpastry.rice.pastry.leafset;

import org.urajio.freshpastry.rice.pastry.*;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * A set of nodes, ordered by numerical distance of their Id from the local
 * Id
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 * @version $Id$
 */

public class SimilarSet extends Observable implements NodeSetEventSource, Serializable /*, Observer*/ {

    private static final long serialVersionUID = 2289610430696506873L;
    transient ArrayList<NodeSetListener> listeners = new ArrayList<>();
    /**
     * Numerically closest node to a given a node. Returns -1 if the local Id
     * is the most similar and returns an index otherwise.
     *
     * @param nid a node id.
     * @return -1 if the local Id is most similar, else the index of the most
     * similar node.
     */
//  public int mostSimilar(Id nid) {
//    if (theSize == 0)
//      return -1;
//
//    Id.Distance minDist = ln.getNodeId().distance(nid);
//    int min = -1;
//
//    for (int i = 0; i < theSize; i++) {
//      Id.Distance d = nodes[i].getNodeId().distance(nid);
//      int cmp = d.compareTo(minDist);
//      if ((!clockwise && cmp < 0) || (clockwise && cmp <= 0)) {
//        minDist = d;
//        min = i;
//      }
//    }
//
//    return min;
//  }

    transient Id.Distance d1 = new Id.Distance();
    transient Id.Distance d = new Id.Distance();
    private NodeHandle ln;
    private boolean clockwise;
    private NodeHandle[] nodes;
    private int theSize;
    private LeafSet leafSet;

    private SimilarSet(SimilarSet that, LeafSet ls) {
        this.ln = that.ln;
        this.clockwise = that.clockwise;
        this.nodes = new NodeHandle[that.nodes.length];
        System.arraycopy(that.nodes, 0, nodes, 0, nodes.length);
        this.theSize = that.theSize;
        this.leafSet = ls;
    }

    /**
     * Constructor.
     *
     * @param localNode the local node
     * @param size      the size of the similar set.
     * @param cw        true if this is the clockwise leafset half
     */

    public SimilarSet(LeafSet leafSet, NodeHandle localNode, int size, boolean cw) {
        this.leafSet = leafSet;
        ln = localNode;
        clockwise = cw;
        theSize = 0;
        nodes = new NodeHandle[size];
    }

    public SimilarSet(LeafSet leafSet, NodeHandle localNode, int size, boolean cw, NodeHandle[] handles) {
        this.leafSet = leafSet;
        ln = localNode;
        clockwise = cw;
        theSize = Math.min(handles.length, size); //handles.length;
        nodes = new NodeHandle[size];
        System.arraycopy(handles, 0, nodes, 0, theSize);
    }

    /**
     * swap two elements
     *
     * @param i the index of the first element
     * @param j the index of the second element
     */

    protected void swap(int i, int j) {
        NodeHandle handle = nodes[i];
        nodes[i] = nodes[j];
        nodes[j] = handle;
    }

    /**
     * Test if a NodeHandle belongs into the set. Predicts if a put would succeed.
     *
     * @param handle the handle to test.
     * @return true if a put would succeed, false otherwise.
     */

    public boolean test(NodeHandle handle) {
        Id nid = handle.getNodeId();

        if (nid.equals(ln.getNodeId()))
            return false;

        for (int i = 0; i < theSize; i++)
            if (nid.equals(nodes[i].getNodeId()))
                return false;

        if (theSize < nodes.length)
            return true;

        if (clockwise) {
            return nid.isBetween(ln.getNodeId(), nodes[theSize - 1].getNodeId());
        } else {
            return nid.isBetween(nodes[theSize - 1].getNodeId(), ln.getNodeId());
        }

    }

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     * @return true if the put succeeded, false otherwise.
     */
    public boolean put(NodeHandle handle) {
        return put(handle, false);
    }

    public boolean put(NodeHandle handle, boolean suppressNotify) {
        Id nid = handle.getNodeId();
        //int index;

        if (!test(handle))
            return false;

        if (theSize < nodes.length) {
            nodes[theSize] = handle;
            //index = theSize;
            theSize++;
        } else {

            NodeHandle removed = nodes[theSize - 1];

            nodes[theSize - 1] = handle;

            if (leafSet.isProperlyRemoved(removed)) {
                if (leafSet.observe) {
                    notifyListeners(removed, false);
                }
            }
//      if (leafSet.observe)
//        nodes[theSize].deleteObserver(this);

            //index = theSize-1;
        }

        // bubble the new node into the correct position
        if (clockwise) {
            for (int i = theSize - 1; i > 0; i--)
                if (nid.isBetween(ln.getNodeId(), nodes[i - 1].getNodeId()))
                    swap(i, i - 1);
                else
                    break;
        } else {
            for (int i = theSize - 1; i > 0; i--)
                if (nid.isBetween(nodes[i - 1].getNodeId(), ln.getNodeId()))
                    swap(i, i - 1);
                else
                    break;
        }

        if (!suppressNotify && !leafSet.testOtherSet(this, handle)) {
            if (leafSet.observe)
                notifyListeners(handle, true);
        }

        // register as an observer, so we'll be notified if the handle is declared
        // dead
//    if (leafSet.observe)
//      handle.addObserver(this);

        return true;
    }

    /**
     * Generates too many objects to use this interface
     *
     * @deprecated use addNodeSetListener
     */
    public void addObserver(Observer o) {
//    logger.warn("WARNING: Observer on RoutingTable is deprecated");
        super.addObserver(o);
    }

    /**
     * Generates too many objects to use this interface
     *
     * @deprecated use removeNodeSetListener
     */
    public void deleteObserver(Observer o) {
//    logger.warn("WARNING: Observer on RoutingTable is deprecated");
        super.deleteObserver(o);
    }

    public void addNodeSetListener(NodeSetListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Is called by the Observer pattern whenever the liveness or proximity of a
     * registered node handle is changed.
     *
     * @param o   The node handle
     * @param arg the event type (PROXIMITY_CHANGE, DECLARED_LIVE, DECLARED_DEAD)
     */
//  public void update(Observable o, Object arg) {
//    if (o instanceof NodeHandle) {
//      // if the node is declared dead, remove it immediately
//      if (((Integer) arg) == NodeHandle.DECLARED_DEAD) {
//        remove((NodeHandle) o);
//      }
//      if (((Integer) arg) == NodeHandle.DECLARED_LIVE) {
//        leafSet.put((NodeHandle) o);
//      }
//    }
//  }
    public void removeNodeSetListener(NodeSetListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    protected void notifyListeners(NodeHandle handle, boolean added) {
        // pass the event to the Observers of this RoutingTable
        synchronized (listeners) {
            for (NodeSetListener listener : listeners) {
                listener.nodeSetUpdate(this, handle, added);
            }
        }
        // handle deprecated interface
        if (countObservers() > 0) {
            setChanged();
            notifyObservers(new NodeSetUpdate(handle, added));
        }
    }

    /**
     * Finds the NodeHandle associated with the Id.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is
     * found.
     */
    public NodeHandle get(Id nid) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].getNodeId().equals(nid))
                return nodes[i];

        return null;
    }

    public NodeHandle get(NodeHandle nh) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].equals(nh))
                return nodes[i];

        return null;
    }

    /**
     * Gets the ith element in the set.
     *
     * @param i an index. i == -1 refers to the local node
     * @return the handle associated with that id or null if no such handle is
     * found.
     */

    public NodeHandle get(int i) {
        if (i < -1 || i >= theSize)
            return null;
        if (i == -1)
            return ln;

        return nodes[i];
    }

    /**
     * Verifies if the set contains this particular id.
     *
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */
    public boolean member(NodeHandle nid) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].equals(nid))
                return true;

        return false;
    }

    /**
     *
     */
    public boolean member(Id nid) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].getId().equals(nid))
                return true;

        return false;
    }

    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the node handle removed or null if nothing.
     */
    public NodeHandle remove(Id nid) {
        for (int i = 0; i < theSize; i++) {
            if (nodes[i].getNodeId().equals(nid)) {
                return remove(i);
            }
        }

        return null;
    }

    public NodeHandle remove(NodeHandle nh) {
//    try {
        for (int i = 0; i < theSize; i++) {
            if (nodes[i].equals(nh)) {
                return remove(i);
            }
        }
        return null;
//    } finally {
//      findMoreEntriesFromRoutingTable();
//    }
    }

    /**
     * The purpose of this code is to keep the ends of leafset in better shape by adding
     * routing table entries if there are holes in the leafset.  Otherwise,
     * sometimes the leafset shows a very small ring size, because a node
     * that should go in the opposite side, gets added to the near side
     * <p>
     * this will confuse density checking code
     */
    void findMoreEntriesFromRoutingTable() {
        if (theSize < nodes.length) {
            // ok, we have room for some entries
            RoutingTable table = leafSet.routingTable;
            if (table == null) return; // don't have this feature installed

            int numRows = table.numRows();
            Id lnId = (org.urajio.freshpastry.rice.pastry.Id) ln.getId();
            int numCols = table.numColumns();
            int baseBitLength = table.baseBitLength();

            // TODO: make sure these entries are alive

            // crawl from bottom of the table to the top
            for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
                RouteSet[] row = table.getRow(rowIndex);
                if (row != null) {
                    int colIndexStart = lnId.getDigit(rowIndex, baseBitLength);
                    // note, don't want to include colIndexStart, it's a waste of time
                    if (clockwise) {
                        for (int colIndex = colIndexStart + 1; colIndex < numCols; colIndex++) {
                            if (addNextEntry(row[colIndex])) return;
                        }
                    } else {
                        for (int colIndex = colIndexStart - 1; colIndex >= 0; colIndex--) {
                            if (addNextEntry(row[colIndex])) return;
                        }
                    }
                } // fi (row != null)
            }

            // crawl back up from bottom of the table
            for (int rowIndex = numRows - 1; rowIndex >= 0; rowIndex--) {
                RouteSet[] row = table.getRow(rowIndex);
                if (row != null) {
                    int colIndexEnd = lnId.getDigit(rowIndex, baseBitLength);
                    // note, don't want to include colIndexEnd, it's a waste of time
                    if (clockwise) {
                        for (int colIndex = 0; colIndex < colIndexEnd; colIndex++) {
                            if (addNextEntry(row[colIndex])) return;
                        }
                    } else {
                        for (int colIndex = numCols - 1; colIndex > colIndexEnd; colIndex--) {
                            if (addNextEntry(row[colIndex])) return;
                        }
                    }
                } // fi (row != null)
            }
        }
    }

    /**
     * Return true if done
     *
     * @param rs
     * @return
     */
    private boolean addNextEntry(RouteSet rs) {
        if (rs == null) return false;

        // the fast case
        if (rs.size() == 1) {
            NodeHandle nh = rs.get(0);
            if (nh.isAlive()) {
                put(nh);
            }
            return theSize == nodes.length;
        }

        // the complicated case: we have to sort the live entries
        ArrayList<NodeHandle> toUse = new ArrayList<>();
        for (int index = 0; index < rs.size(); index++) {
            NodeHandle nh = rs.get(index);
            if (nh.isAlive())
                toUse.add(nh);
        }

        switch (toUse.size()) {
            case 0:
                return false;
            case 1: {
                NodeHandle nh = toUse.get(0);
                put(nh);
            }
            break;
            default:
                // more than 1 node, and both are live, need to sort by direction
                toUse.sort(new Comparator<NodeHandle>() {

                    public int compare(NodeHandle a, NodeHandle b) {
                        // smallest to biggest: TODO, verify this does the right thing... it's pretty minor though...
                        if (clockwise) return a.getId().compareTo(b.getId());
                        return b.getId().compareTo(a.getId());
                    }
                });
                for (NodeHandle nh : toUse) {
                    put(nh);
                }
        }
        return theSize == nodes.length;
    }

    /**
     * Removes a node id and its handle from the set.
     *
     * @param i the index of the node to remove.
     * @return the node handle removed or null if nothing.
     */

    protected NodeHandle remove(int i) {
        if (i < 0 || i >= theSize)
            return null;
        NodeHandle handle = nodes[i];

        if (theSize - i + 1 >= 0) {
            System.arraycopy(nodes, i + 1, nodes, i + 1 - 1, theSize - i + 1);
        }

        theSize--;

        if (leafSet.isProperlyRemoved(handle)) {
            if (leafSet.observe)
                notifyListeners(handle, false);
        }

//    if (leafSet.observe)
//      handle.deleteObserver(this);

        return handle;
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     * @return the index or -1 if the element does not exist.
     */

    public int getIndex(Id nid) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].getNodeId().equals(nid))
                return i;

        return -1;
    }

    public int getIndex(NodeHandle nh) {
        for (int i = 0; i < theSize; i++)
            if (nodes[i].equals(nh))
                return i;

        return -1;
    }

    /**
     * Gets the current size of this set.
     *
     * @return the size.
     */

    public int size() {
        return theSize;
    }

    /**
     * Impl that doesn't produce garbage
     * <p>
     * Numerically closest node to a given a node. Returns -1 if the local Id
     * is the most similar and returns an index otherwise.
     *
     * @param nid a node id.
     * @return -1 if the local Id is most similar, else the index of the most
     * similar node.
     */
    public int mostSimilar(Id nid) {
        if (theSize == 0)
            return -1;

        Id.Distance other = d;
        Id.Distance minDist = ln.getNodeId().distance(nid, d1);
        int min = -1;

        for (int i = 0; i < theSize; i++) {
            other = nodes[i].getNodeId().distance(nid, other);
            int cmp = other.compareTo(minDist);
            if ((!clockwise && cmp < 0) || (clockwise && cmp <= 0)) {
                // swap buffers
                Id.Distance tmp = minDist;
                minDist = other;
                other = tmp;

                min = i;
            }
        }

        return min;
    }

    // Common API Support

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
        return member((Id) id);
    }

    /**
     * Removes a node id and its handle from the set.
     *
     * @param id the node to remove.
     * @return the node handle removed or null if nothing.
     */
    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle removeHandle(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        return remove((Id) id);
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

    SimilarSet copy(LeafSet newLeafSet) {
        return new SimilarSet(this, newLeafSet);
    }

    /**
     * This is thread safe, in that it won't throw an error if not properly synchronized.
     *
     * @return
     */
    public Collection<NodeHandle> getCollection() {
        ArrayList<NodeHandle> al = new ArrayList<>();
        for (int i = 0; i < theSize; i++) {
            NodeHandle nh = nodes[i];
            if (nh != null) {
                al.add(nh);
            }
        }
        return al;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        d1 = new Id.Distance();
        d = new Id.Distance();
        listeners = new ArrayList<>();
    }

    public String toString() {
        return "SimilarSet{" + ln + "}";
    }

    public void destroy() {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
//        nodes[i].deleteObserver(this); 
                nodes[i] = null;
            }
        }
        theSize = 0;
    }

}

