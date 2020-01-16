package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import java.io.IOException;

/**
 * Thrown when we can't find a way to very connectivity by a 3rd party.
 *
 * @author Jeff Hoye
 */
public class CantVerifyConnectivityException extends IOException {

    public CantVerifyConnectivityException(String s) {
        super(s);
    }

}
