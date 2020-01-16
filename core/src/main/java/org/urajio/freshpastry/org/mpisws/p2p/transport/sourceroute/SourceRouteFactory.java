package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;

import java.io.IOException;
import java.util.List;

public interface SourceRouteFactory<Identifier> {
    SourceRoute<Identifier> getSourceRoute(List<Identifier> route);

    SourceRoute<Identifier> reverse(SourceRoute<Identifier> route);

    SourceRoute<Identifier> build(InputBuffer buf, Identifier local, Identifier lastHop) throws IOException;

    SourceRoute<Identifier> getSourceRoute(Identifier local, Identifier dest);

    SourceRoute<Identifier> getSourceRoute(Identifier local);
}
