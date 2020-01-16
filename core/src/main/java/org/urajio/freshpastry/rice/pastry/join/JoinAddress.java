package org.urajio.freshpastry.rice.pastry.join;

/**
 * The address of the join receiver at a pastry node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */

public class JoinAddress {
    private static final int myCode = 0xe80c17e8;

    public static int getCode() {
        return myCode;
    }
}