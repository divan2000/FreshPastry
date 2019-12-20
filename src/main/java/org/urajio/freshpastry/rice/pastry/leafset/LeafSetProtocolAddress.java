package org.urajio.freshpastry.rice.pastry.leafset;

/**
 * The address of the leafset protocol at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class LeafSetProtocolAddress {
  private static final int myCode = 0xf921def1;

  public static int getCode() {
    return myCode;
  }
}