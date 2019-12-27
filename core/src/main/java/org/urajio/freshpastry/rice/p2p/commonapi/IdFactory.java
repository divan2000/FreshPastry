package org.urajio.freshpastry.rice.p2p.commonapi;

import org.urajio.freshpastry.rice.environment.random.RandomSource;

import java.util.Random;
import java.util.SortedMap;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * @(#) IdFactory.java This interface provides applications with a way of generating Ids in a
 * protocol-specific manner.
 */
public interface IdFactory {

    /**
     * Builds a protocol-specific Id given the source data.
     *
     * @param material The material to use
     * @return The built Id.
     */
    Id buildId(byte[] material);

    /**
     * Builds a protocol-specific Id given the source data.
     *
     * @param material The material to use
     * @return The built Id.
     */
    Id buildId(int[] material);

    /**
     * Builds a protocol-specific Id by using the hash of the given string as source data.
     *
     * @param string The string to use as source data
     * @return The built Id.
     */
    Id buildId(String string);

    /**
     * Builds a random protocol-specific Id.
     *
     * @param rng A random number generator
     * @return The built Id.
     */
    Id buildRandomId(Random rng);

    Id buildRandomId(RandomSource rng);

    /**
     * Builds an Id by converting the given toString() output back to an Id.  Should
     * not normally be used.
     *
     * @param string The toString() representation of an Id
     * @return The built Id.
     */
    Id buildIdFromToString(String string);

    /**
     * Builds an Id by converting the given toString() output back to an Id.  Should
     * not normally be used.
     *
     * @param chars  The character array
     * @param offset The offset to start reading at
     * @param length The length to read
     * @return The built Id.
     */
    Id buildIdFromToString(char[] chars, int offset, int length);

    /**
     * Builds an IdRange based on a prefix.  Any id which has this prefix should
     * be inside this IdRange, and any id which does not share this prefix should
     * be outside it.
     *
     * @param string The toString() representation of an Id
     * @return The built Id.
     */
    IdRange buildIdRangeFromPrefix(String string);

    /**
     * Returns the length a Id.toString should be.
     *
     * @return The correct length;
     */
    int getIdToStringLength();

    /**
     * Builds a protocol-specific Id.Distance given the source data.
     *
     * @param material The material to use
     * @return The built Id.Distance.
     */
    Id.Distance buildIdDistance(byte[] material);

    /**
     * Creates an IdRange given the CW and CCW ids.
     *
     * @param cw  The clockwise Id
     * @param ccw The counterclockwise Id
     * @return An IdRange with the appropriate delimiters.
     */
    IdRange buildIdRange(Id cw, Id ccw);

    /**
     * Creates an empty IdSet.
     *
     * @return an empty IdSet
     */
    IdSet buildIdSet();

    /**
     * Creates an empty IdSet.
     *
     * @return an empty IdSet
     * @Param map The map which to take the keys from to create the IdSet's elements
     */
    IdSet buildIdSet(SortedMap map);

    /**
     * Creates an empty NodeHandleSet.
     *
     * @return an empty NodeHandleSet
     */
    NodeHandleSet buildNodeHandleSet();
}

