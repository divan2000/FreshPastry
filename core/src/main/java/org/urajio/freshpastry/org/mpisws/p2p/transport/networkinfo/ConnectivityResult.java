package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import java.net.InetSocketAddress;
import java.util.Map;

public interface ConnectivityResult {
    void udpSuccess(InetSocketAddress from, Map<String, Object> options);

    void tcpSuccess(InetSocketAddress from, Map<String, Object> options);

    void receiveException(Exception e);
}
