/*******************************************************************************

 "FreePastry" Peer-to-Peer Application Development Substrate

 Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute
 for Software Systems.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:

 - Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.

 - Neither the name of Rice  University (RICE), Max Planck Institute for Software
 Systems (MPI-SWS) nor the names of its contributors may be used to endorse or
 promote products derived from this software without specific prior written
 permission.

 This software is provided by RICE, MPI-SWS and the contributors on an "as is"
 basis, without any representations or warranties of any kind, express or implied
 including, but not limited to, representations or warranties of
 non-infringement, merchantability or fitness for a particular purpose. In no
 event shall RICE, MPI-SWS or contributors be liable for any direct, indirect,
 incidental, special, exemplary, or consequential damages (including, but not
 limited to, procurement of substitute goods or services; loss of use, data, or
 profits; or business interruption) however caused and on any theory of
 liability, whether in contract, strict liability, or tort (including negligence
 or otherwise) arising in any way out of the use of this software, even if
 advised of the possibility of such damage.
 *******************************************************************************/

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

