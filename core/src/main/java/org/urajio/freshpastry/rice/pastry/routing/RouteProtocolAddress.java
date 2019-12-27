package org.urajio.freshpastry.rice.pastry.routing;

/**
 * The address of the route protocol at a pastry node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class RouteProtocolAddress {
    private static final int myCode = 0x89ce110e;

    public static int getCode() {
        return myCode;
    }
}