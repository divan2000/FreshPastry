package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandle;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandleFactory;

import java.io.IOException;

public class RendezvousSNHFactory extends SocketNodeHandleFactory {

    public RendezvousSNHFactory(PastryNode pn) {
        super(pn);
    }

    @Override
    public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id) {
        return getNodeHandle(i, epoch, id, (byte) 0);
    }

    public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id, byte contactState) {
        SocketNodeHandle handle = new RendezvousSocketNodeHandle(i, epoch, id, pn, contactState);

        return coalesce(handle);
    }

    @Override
    public SocketNodeHandle readNodeHandle(InputBuffer buf) throws IOException {
        return coalesce(RendezvousSocketNodeHandle.build(buf, pn));
    }
}
