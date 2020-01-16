package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager;

import org.urajio.freshpastry.org.mpisws.p2p.transport.ErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketWrapperSocket;

public class SourceRouteManagerP2PSocket<Identifier> extends SocketWrapperSocket<Identifier, SourceRoute<Identifier>> {
    public SourceRouteManagerP2PSocket(P2PSocket<SourceRoute<Identifier>> socket, ErrorHandler<Identifier> errorHandler) {
        super(socket.getIdentifier().getLastHop(), socket, errorHandler, socket.getOptions());
    }
}
