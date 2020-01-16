package org.urajio.freshpastry.rice.p2p.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * @(#) Id.java This interface is an abstraction of an Id (or key) from the CommonAPI paper.
 */
public interface Id extends Comparable<Id>, Serializable {

    /**
     * Checks if this Id is between two given ids ccw (inclusive) and cw (exclusive) on the circle
     *
     * @param ccw the counterclockwise id
     * @param cw  the clockwise id
     * @return true if this is between ccw (inclusive) and cw (exclusive), false otherwise
     */
    boolean isBetween(Id ccw, Id cw);

    /**
     * Checks to see if the Id nid is clockwise or counterclockwise from this, on the ring. An Id is
     * clockwise if it is within the half circle clockwise from this on the ring. An Id is considered
     * counter-clockwise from itself.
     *
     * @param nid The id to compare to
     * @return true if clockwise, false otherwise.
     */
    boolean clockwise(Id nid);

    /**
     * Returns an Id corresponding to this Id plus a given distance
     *
     * @param offset the distance to add
     * @return the new Id
     */
    Id addToId(Distance offset);

    /**
     * Returns the shorter numerical distance on the ring between a pair of Ids.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */
    Distance distanceFromId(Id nid);

    /**
     * Returns the longer numerical distance on the ring between a pair of Ids.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */
    Distance longDistanceFromId(Id nid);

    /**
     * Returns a (mutable) byte array representing this Id
     *
     * @return A byte[] representing this Id
     */
    byte[] toByteArray();

    /**
     * Stores the byte[] value of this Id in the provided byte array
     *
     * @return A byte[] representing this Id
     */
    void toByteArray(byte[] array, int offset);

    /**
     * Returns the length of the byte[] representing this Id
     *
     * @return The length of the byte[] representing this Id
     */
    int getByteArrayLength();

    /**
     * Returns a string representing the full length of this Id.
     *
     * @return A string with all of this Id
     */
    String toStringFull();

    /**
     * Serialize
     *
     * @param buf
     * @throws IOException
     */
    void serialize(OutputBuffer buf) throws IOException;

    short getType();

    /**
     * A class for representing and manipulating the distance between two Ids on the circle.
     *
     * @author amislove
     * @version $Id$
     */
    interface Distance extends Comparable<Distance>, Serializable {

        /**
         * Shift operator. shift(-1,0) multiplies value of this by two, shift(1,0) divides by 2
         *
         * @param cnt  the number of bits to shift, negative shifts left, positive shifts right
         * @param fill value of bit shifted in (0 if fill == 0, 1 otherwise)
         * @return this
         */
        Distance shiftDistance(int cnt, int fill);
    }

}

