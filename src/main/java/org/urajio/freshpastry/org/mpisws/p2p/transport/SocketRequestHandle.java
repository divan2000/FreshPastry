package org.urajio.freshpastry.org.mpisws.p2p.transport;

import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.util.Map;

/**
 * Can cancel the request to open the socket.  Also, returned with the
 * socket when it has been opened.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface SocketRequestHandle<Identifier> extends Cancellable {
    /**
     * The identifier that the caller requested to open to.
     *
     * @return
     */
    Identifier getIdentifier();

    /**
     * The options that the caller used.
     *
     * @return
     */
    Map<String, Object> getOptions();
}
