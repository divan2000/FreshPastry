package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import java.util.Map;

/**
 * Called when a ping is received.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public interface PingListener<Identifier> {
    /**
     * Pong received.
     *
     * @param i       Where the ping was from
     * @param rtt     the RTT
     * @param options how the ping was sent (source route/udp etc)
     */
    void pingResponse(Identifier i, int rtt, Map<String, Object> options);

    /**
     * Called when we receive a ping (not a pong)
     *
     * @param i
     * @param options
     */
    void pingReceived(Identifier i, Map<String, Object> options);
}
