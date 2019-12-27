package org.urajio.freshpastry.rice.pastry.routing;

/**
 * The address of the router at a pastry node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class RouterAddress {
    private static final int myCode = 0xACBDFE17;

    public static int getCode() {
        return myCode;
    }
}