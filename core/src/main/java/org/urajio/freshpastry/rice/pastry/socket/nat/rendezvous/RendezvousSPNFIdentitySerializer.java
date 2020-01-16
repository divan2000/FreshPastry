package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.socket.SPNFIdentitySerializer;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandle;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.socket.TransportLayerNodeHandle;

import java.io.IOException;

public class RendezvousSPNFIdentitySerializer extends SPNFIdentitySerializer {

    protected RendezvousSPNFIdentitySerializer(PastryNode pn, SocketNodeHandleFactory factory) {
        super(pn, factory);
    }

    @Override
    public void serialize(OutputBuffer buf,
                          TransportLayerNodeHandle<MultiInetSocketAddress> i)
            throws IOException {
        super.serialize(buf, i);
        buf.writeByte(((RendezvousSocketNodeHandle) i).getContactStatus());
    }

    @Override
    protected SocketNodeHandle buildSNH(InputBuffer buf, MultiInetSocketAddress i, long epoch, Id nid) throws IOException {
        return new RendezvousSocketNodeHandle(i, epoch, nid, pn, buf.readByte());
    }
}
