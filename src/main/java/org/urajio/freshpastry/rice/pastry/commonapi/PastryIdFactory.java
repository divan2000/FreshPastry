package org.urajio.freshpastry.rice.pastry.commonapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.*;

import java.util.*;
import java.security.*;

/**
 * This class provides applications with a way of genertating
 * pastry Ids.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class PastryIdFactory implements IdFactory {
  private final static Logger logger = LoggerFactory.getLogger(PastryIdFactory.class);

  private MessageDigest md;

  /**
   * Constructor
   */
  public PastryIdFactory(Environment env) {
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      logger.error("No SHA support!" );
    }
  }
      
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(byte[] material) {
    return org.urajio.freshpastry.rice.pastry.Id.build(material);
  }

  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(int[] material) {
    return org.urajio.freshpastry.rice.pastry.Id.build(material);
  }

  /**
   * Builds a protocol-specific Id by using the hash of the given string as source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public Id buildId(String string) {
    synchronized (md) {
      md.update(string.getBytes());
      return buildId(md.digest());
    }
  }
  
  /**
   * Builds a random protocol-specific Id.
   *
   * @param rng A random number generator
   * @return The built Id.
   */
  public org.urajio.freshpastry.rice.p2p.commonapi.Id buildRandomId(Random rng) {
    return org.urajio.freshpastry.rice.pastry.Id.makeRandomId(rng);
  }

  public org.urajio.freshpastry.rice.p2p.commonapi.Id buildRandomId(RandomSource rng) {
    return org.urajio.freshpastry.rice.pastry.Id.makeRandomId(rng);
  }

  /**
   * Builds an Id by converting the given toString() output back to an Id.  Should
   * not normally be used.
   *
   * @param string The toString() representation of an Id
   * @return The built Id.
   */
  public Id buildIdFromToString(String string) {
    return org.urajio.freshpastry.rice.pastry.Id.build(string);
  }
  
  /**
   * Builds an Id by converting the given toString() output back to an Id.  Should
   * not normally be used.
   *
   * @param chars The character array
   * @param offset The offset to start reading at
   * @param length The length to read
   * @return The built Id.
   */
  public Id buildIdFromToString(char[] chars, int offset, int length) {
    return org.urajio.freshpastry.rice.pastry.Id.build(chars, offset, length);
  } 
  
  /**
   * Builds an IdRange based on a prefix.  Any id which has this prefix should
   * be inside this IdRange, and any id which does not share this prefix should
   * be outside it.
   *
   * @param string The toString() representation of an Id
   * @return The built Id.
   */
  public IdRange buildIdRangeFromPrefix(String string) {
    org.urajio.freshpastry.rice.pastry.Id start = org.urajio.freshpastry.rice.pastry.Id.build(string);

    org.urajio.freshpastry.rice.pastry.Id end = org.urajio.freshpastry.rice.pastry.Id.build(string + "ffffffffffffffffffffffffffffffffffffffff");
        
    end = end.getCW();
    
    return new org.urajio.freshpastry.rice.pastry.IdRange(start, end);
  }
  
  /**
   * Returns the length a Id.toString should be.
   *
   * @return The correct length;
   */
  public int getIdToStringLength() {
    return org.urajio.freshpastry.rice.pastry.Id.IdBitLength/4;
  }
  
  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public Id.Distance buildIdDistance(byte[] material) {
    return new org.urajio.freshpastry.rice.pastry.Id.Distance(material);
  }

  /**
   * Creates an IdRange given the CW and CCW ids.
   *
   * @param cw The clockwise Id
   * @param ccw The counterclockwise Id
   * @return An IdRange with the appropriate delimiters.
   */
  public IdRange buildIdRange(Id cw, Id ccw) {
    return new org.urajio.freshpastry.rice.pastry.IdRange((org.urajio.freshpastry.rice.pastry.Id) cw, (org.urajio.freshpastry.rice.pastry.Id) ccw);
  }

  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet() {
    return new org.urajio.freshpastry.rice.pastry.IdSet();
  }
  
  /**
    * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet(SortedMap map) {
    return new org.urajio.freshpastry.rice.pastry.IdSet(map);
  }
  
  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet() {
    return new org.urajio.freshpastry.rice.pastry.NodeSet();
  }
}

