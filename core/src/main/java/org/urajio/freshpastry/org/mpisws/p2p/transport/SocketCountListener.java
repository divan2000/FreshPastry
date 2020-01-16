package org.urajio.freshpastry.org.mpisws.p2p.transport;

import java.util.Map;

public interface SocketCountListener<Identifier> {
    void socketOpened(Identifier i, Map<String, Object> options, boolean outgoing);

    void socketClosed(Identifier i, Map<String, Object> options);
}
