package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import java.io.IOException;

/**
 * Wraps a normal exception in an IOException
 *
 * @author Jeff Hoye
 */
public class NetworkInfoIOException extends IOException {
    public NetworkInfoIOException(Exception e) {
        initCause(e);
    }
}
