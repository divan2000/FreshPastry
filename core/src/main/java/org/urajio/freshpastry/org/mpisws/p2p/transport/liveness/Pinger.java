package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import java.util.Map;

public interface Pinger<Identifier> {
    /**
     * @param i       the identifier that responded
     * @param options transport layer dependent way to send the ping (udp/tcp etc)
     * @return true If the ping will occur.  (Maybe it won't due to bandwidth concerns.)
     */
    boolean ping(Identifier i, Map<String, Object> options);

    void addPingListener(PingListener<Identifier> name);

    boolean removePingListener(PingListener<Identifier> name);
}
