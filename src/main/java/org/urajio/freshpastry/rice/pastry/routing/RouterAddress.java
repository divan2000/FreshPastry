package org.urajio.freshpastry.rice.pastry.routing;

/**
 * The address of the router at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RouterAddress {
  private static final int myCode = 0xACBDFE17;

  public static int getCode() {
    return myCode; 
  }  
}