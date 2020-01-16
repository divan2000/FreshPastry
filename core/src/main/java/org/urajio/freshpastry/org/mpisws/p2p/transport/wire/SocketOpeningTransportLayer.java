package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketCountListener;

public interface SocketOpeningTransportLayer<Identifier> {
    void addSocketCountListener(SocketCountListener<Identifier> listener);

    void removeSocketCountListener(SocketCountListener<Identifier> listener);
}
