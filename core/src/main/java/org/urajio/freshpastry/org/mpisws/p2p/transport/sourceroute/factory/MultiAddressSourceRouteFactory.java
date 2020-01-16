package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.factory;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiAddressSourceRouteFactory implements SourceRouteFactory<MultiInetSocketAddress> {

    /**
     * 2 in the path is a special case, and we can just generate it from the local and last hops
     */
    public SourceRoute<MultiInetSocketAddress> build(InputBuffer buf, MultiInetSocketAddress local, MultiInetSocketAddress lastHop) throws IOException {
        byte numInPath = buf.readByte();
        if (numInPath == 2) {
            return new MultiAddressSourceRoute(lastHop, local);
        }

        ArrayList<MultiInetSocketAddress> path = new ArrayList<>(numInPath);
        for (int i = 0; i < numInPath; i++) {
            path.add(MultiInetSocketAddress.build(buf));
        }
        return new MultiAddressSourceRoute(path);
    }

    public SourceRoute<MultiInetSocketAddress> getSourceRoute(List<MultiInetSocketAddress> route) {
        return new MultiAddressSourceRoute(route);
    }

    public SourceRoute<MultiInetSocketAddress> reverse(SourceRoute<MultiInetSocketAddress> route) {
        MultiAddressSourceRoute temp = (MultiAddressSourceRoute) route;
        ArrayList<MultiInetSocketAddress> result = new ArrayList<>(temp.getPath());

        Collections.reverse(result);

        return new MultiAddressSourceRoute(result);
    }

    public SourceRoute<MultiInetSocketAddress> getSourceRoute(
            MultiInetSocketAddress local, MultiInetSocketAddress dest) {
        return new MultiAddressSourceRoute(local, dest);
    }

    public SourceRoute<MultiInetSocketAddress> getSourceRoute(MultiInetSocketAddress local) {
        return new MultiAddressSourceRoute(local);
    }
}
