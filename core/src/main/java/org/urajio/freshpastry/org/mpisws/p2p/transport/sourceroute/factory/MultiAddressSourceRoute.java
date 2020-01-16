package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.factory;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.util.List;

public class MultiAddressSourceRoute extends SourceRoute<MultiInetSocketAddress> {

    MultiAddressSourceRoute(MultiInetSocketAddress local, MultiInetSocketAddress remote) {
        super(local, remote);
    }

    MultiAddressSourceRoute(List<MultiInetSocketAddress> path) {
        super(path);
    }

    MultiAddressSourceRoute(MultiInetSocketAddress address) {
        super(address);
    }

    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) path.size());
        if (path.size() == 2) return; // special case direct connection
        for (MultiInetSocketAddress i : path) {
            i.serialize(buf);
        }
    }

    public int getSerializedLength() {
        if (path.size() == 2) return 1;
        int ret = 1; // numhops

        // the size of all the EISAs
        for (MultiInetSocketAddress i : path) {
            ret += i.getSerializedLength();
        }
        return ret;
    }

    List<MultiInetSocketAddress> getPath() {
        return path;
    }
}
