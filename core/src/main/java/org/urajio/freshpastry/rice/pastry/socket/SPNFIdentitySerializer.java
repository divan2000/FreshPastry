package org.urajio.freshpastry.rice.pastry.socket;

import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.IdentitySerializer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.identity.SerializerListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactoryListener;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SPNFIdentitySerializer implements
        IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> {
    protected PastryNode pn;

    protected SocketNodeHandleFactory factory;
    Map<SerializerListener<TransportLayerNodeHandle<MultiInetSocketAddress>>, NodeHandleFactoryListener<SocketNodeHandle>> listeners =
            new HashMap<>();

    public SPNFIdentitySerializer(PastryNode pn, SocketNodeHandleFactory factory) {
        this.pn = pn;
        this.factory = factory;
    }

    public void serialize(OutputBuffer buf, TransportLayerNodeHandle<MultiInetSocketAddress> i) throws IOException {

        long epoch = i.getEpoch();
        Id nid = (org.urajio.freshpastry.rice.pastry.Id) i.getId();
        buf.writeLong(epoch);
        nid.serialize(buf);
    }

    /**
     * This is different from the normal deserializer b/c we already have the address
     */
    public TransportLayerNodeHandle<MultiInetSocketAddress> deserialize(
            InputBuffer buf, SourceRoute<MultiInetSocketAddress> i)
            throws IOException {
        long epoch = buf.readLong();
        Id nid = Id.build(buf);

        SocketNodeHandle ret = buildSNH(buf, i.getLastHop(), epoch, nid);
        return factory.coalesce(ret);
    }

    protected SocketNodeHandle buildSNH(InputBuffer buf, MultiInetSocketAddress i, long epoch, Id nid) throws IOException {
        return new SocketNodeHandle(i, epoch, nid, pn);
    }

    public MultiInetSocketAddress translateDown(TransportLayerNodeHandle<MultiInetSocketAddress> i) {
        return i.getAddress();
    }

    public MultiInetSocketAddress translateUp(SourceRoute<MultiInetSocketAddress> i) {
        return i.getLastHop();
    }

    public void addSerializerListener(final SerializerListener<TransportLayerNodeHandle<MultiInetSocketAddress>> listener) {
        NodeHandleFactoryListener<SocketNodeHandle> foo = new NodeHandleFactoryListener<SocketNodeHandle>() {
            public void nodeHandleFound(SocketNodeHandle nodeHandle) {
                listener.nodeHandleFound(nodeHandle);
            }
        };
        listeners.put(listener, foo);
        factory.addNodeHandleFactoryListener(foo);
    }

    public void removeSerializerListener(final SerializerListener<TransportLayerNodeHandle<MultiInetSocketAddress>> listener) {
        factory.removeNodeHandleFactoryListener(listeners.get(listener));
    }
}
