package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;

import java.nio.ByteBuffer;

public interface SourceRouteTransportLayer<Identifier> extends TransportLayer<SourceRoute<Identifier>, ByteBuffer> {
    String OPTION_SOURCE_ROUTE = "source_route";
    int DONT_SOURCE_ROUTE = 0; // direct only
    int ALLOW_SOURCE_ROUTE = 1;

    void addSourceRouteTap(SourceRouteTap tap);

    boolean removeSourceRouteTap(SourceRouteTap tap);
}
