package org.urajio.freshpastry.rice.p2p.commonapi;

import java.io.Serializable;
import java.util.Iterator;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * @(#) IdSet.java
 * <p>
 * Represents a set of ids.
 */
public interface IdSet extends Serializable {

    /**
     * return the number of elements
     */
    int numElements();

    /**
     * add a member
     *
     * @param id the id to add
     */
    void addId(Id id);

    /**
     * remove a member
     *
     * @param id the id to remove
     */
    void removeId(Id id);

    /**
     * test membership
     *
     * @param id the id to test
     * @return true of id is a member, false otherwise
     */
    boolean isMemberId(Id id);

    /**
     * return a subset of this set, consisting of the member ids in a given range
     */
    IdSet subSet(IdRange range);

    /**
     * return an iterator over the elements of this set
     *
     * @return the interator
     */
    Iterator<Id> getIterator();

    /**
     * return this set as an array
     *
     * @return the array
     */
    Id[] asArray();

    /**
     * return a hash of this set
     *
     * @return the hash of this set
     */
    byte[] hash();

    /**
     * Override clone() to make it publicly accessible
     *
     * @return A clone of this set
     */
    Object clone();

    /**
     * Returns a new, empty IdSet of this type
     *
     * @return A new IdSet
     */
    IdSet build();
}
