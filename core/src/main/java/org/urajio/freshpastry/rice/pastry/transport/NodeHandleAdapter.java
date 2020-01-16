package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.boot.Bootstrapper;

import java.util.Map;

public class NodeHandleAdapter implements
        TransportLayer<NodeHandle, RawMessage>,
        LivenessProvider<NodeHandle>,
        ProximityProvider<NodeHandle> {

    TransportLayer tl;
    LivenessProvider livenessProvider;
    ProximityProvider proxProvider;
    Bootstrapper boot;

    public NodeHandleAdapter(TransportLayer tl, LivenessProvider livenessProvider, ProximityProvider proxProvider) {
        this.tl = tl;
        this.livenessProvider = livenessProvider;
        this.proxProvider = proxProvider;
    }

    public void acceptMessages(boolean b) {
        tl.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        tl.acceptSockets(b);
    }

    public NodeHandle getLocalIdentifier() {
        return (NodeHandle) tl.getLocalIdentifier();
    }

    public SocketRequestHandle<NodeHandle> openSocket(NodeHandle i, SocketCallback<NodeHandle> deliverSocketToMe, Map<String, Object> options) {
        return tl.openSocket(i, deliverSocketToMe, options);
    }

    public MessageRequestHandle<NodeHandle, RawMessage> sendMessage(NodeHandle i, RawMessage m, MessageCallback<NodeHandle, RawMessage> deliverAckToMe, Map<String, Object> options) {
        return tl.sendMessage(i, m, deliverAckToMe, options);
    }

    public void setCallback(TransportLayerCallback<NodeHandle, RawMessage> callback) {
        tl.setCallback(callback);
    }

    public void setErrorHandler(ErrorHandler<NodeHandle> handler) {
        tl.setErrorHandler(handler);
    }

    public void destroy() {
        tl.destroy();
    }

    public void addLivenessListener(LivenessListener<NodeHandle> name) {
        livenessProvider.addLivenessListener(name);
    }

    public boolean checkLiveness(NodeHandle i, Map<String, Object> options) {
        return livenessProvider.checkLiveness(i, options);
    }

    public int getLiveness(NodeHandle i, Map<String, Object> options) {
        return livenessProvider.getLiveness(i, options);
    }

    public boolean removeLivenessListener(LivenessListener<NodeHandle> name) {
        return livenessProvider.removeLivenessListener(name);
    }

    public void addProximityListener(ProximityListener<NodeHandle> listener) {
        proxProvider.addProximityListener(listener);
    }

    public int proximity(NodeHandle i, Map<String, Object> options) {
        return proxProvider.proximity(i, options);
    }

    public boolean removeProximityListener(ProximityListener<NodeHandle> listener) {
        return proxProvider.removeProximityListener(listener);
    }

    public TransportLayer getTL() {
        return tl;
    }

    public void clearState(NodeHandle i) {
        livenessProvider.clearState(i);
    }
}
