package org.urajio.freshpastry.rice.pastry.socket;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.Serializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactoryListener;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SocketNodeHandleFactory implements NodeHandleFactory<SocketNodeHandle>, Serializer<SocketNodeHandle> {

    protected PastryNode pn;
    protected Map<SocketNodeHandle, SocketNodeHandle> handleSet;
    protected Collection<NodeHandleFactoryListener<SocketNodeHandle>> listeners = new ArrayList<>();

    public SocketNodeHandleFactory(PastryNode pn) {
        this.pn = pn;

        handleSet = new HashMap<>();
    }


    /**
     * This is kind of weird, may need to rethink this.  This is called to build one w/o
     * deserializing anything.  (Either the local node, or one with a bogus id).
     *
     * @param i
     * @param id
     * @return
     */
    public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id) {
        SocketNodeHandle handle = new SocketNodeHandle(i, epoch, id, pn);

        return coalesce(handle);
    }

    public SocketNodeHandle readNodeHandle(InputBuffer buf) throws IOException {
        return coalesce(SocketNodeHandle.build(buf, pn));
    }

    public SocketNodeHandle coalesce(SocketNodeHandle h) {
        if (handleSet.containsKey(h)) {
            return handleSet.get(h);
        }

        h.setLocalNode(pn);

        handleSet.put(h, h);
        notifyListeners(h);
        return h;
    }

    /**
     * Notify the listeners that this new handle has come along.
     */
    protected void notifyListeners(SocketNodeHandle nh) {
        Collection<NodeHandleFactoryListener<SocketNodeHandle>> temp;
        synchronized (listeners) {
            temp = new ArrayList<>(listeners);
        }
        for (NodeHandleFactoryListener<SocketNodeHandle> foo : temp) {
            foo.nodeHandleFound(nh);
        }
    }

    public void addNodeHandleFactoryListener(
            NodeHandleFactoryListener<SocketNodeHandle> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeNodeHandleFactoryListener(
            NodeHandleFactoryListener<SocketNodeHandle> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public SocketNodeHandle deserialize(InputBuffer buf) throws IOException {
        return readNodeHandle(buf);
    }

    public void serialize(SocketNodeHandle i, OutputBuffer buf)
            throws IOException {
        i.serialize(buf);
    }
}
