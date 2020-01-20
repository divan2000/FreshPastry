package org.urajio.freshpastry.examples.transport.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.examples.direct.NetworkSimulator;
import org.urajio.freshpastry.examples.direct.NodeRecord;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

public class DirectTransportLayer<Identifier, MessageType> implements TransportLayer<Identifier, MessageType> {
    private final static Logger logger = LoggerFactory.getLogger(DirectTransportLayer.class);

    protected boolean acceptMessages = true;
    protected boolean acceptSockets = true;

    protected Identifier localIdentifier;
    protected TransportLayerCallback<Identifier, MessageType> callback;
    protected GenericNetworkSimulator<Identifier, MessageType> simulator;
    protected ErrorHandler<Identifier> errorHandler;
    protected LivenessProvider<Identifier> livenessProvider;

    protected Environment environment;
    int seq = Integer.MIN_VALUE;

    public DirectTransportLayer(Identifier local,
                                NetworkSimulator<Identifier, MessageType> simulator,
                                NodeRecord nr, Environment env) {
        this.localIdentifier = local;
        this.simulator = simulator.getGenericSimulator();
        this.livenessProvider = simulator.getLivenessProvider();

        this.environment = env;
        simulator.registerNode(local, this, nr);
    }

    public void acceptMessages(boolean b) {
        acceptMessages = b;
    }

    public void acceptSockets(boolean b) {
        acceptSockets = b;
    }

    public Identifier getLocalIdentifier() {
        return localIdentifier;
    }

    public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
        SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<>(i, options);

        if (simulator.isAlive(i)) {
            int delay = Math.round(simulator.networkDelay(localIdentifier, i));
            DirectAppSocket<Identifier, MessageType> socket = new DirectAppSocket<>(i, localIdentifier, deliverSocketToMe, simulator, handle, options);
            CancelAndClose<Identifier, MessageType> cancelAndClose = new CancelAndClose<>(socket, simulator.enqueueDelivery(socket.getAcceptorDelivery(), delay));
            handle.setSubCancellable(cancelAndClose);
        } else {
            int delay = 5000;  // TODO: Make this configurable
            handle.setSubCancellable(
                    simulator.enqueueDelivery(
                            new ConnectorExceptionDelivery<>(deliverSocketToMe, handle, new SocketTimeoutException()), delay));
        }

        return handle;
    }

    public MessageRequestHandle<Identifier, MessageType> sendMessage(
            Identifier i, MessageType m,
            MessageCallback<Identifier, MessageType> deliverAckToMe,
            Map<String, Object> options) {
        if (!simulator.isAlive(localIdentifier))
            return null; // just make this stop, the local node is dead, he shouldn't be doing anything
        MessageRequestHandleImpl<Identifier, MessageType> handle = new MessageRequestHandleImpl<>(i, m, options);

        if (livenessProvider.getLiveness(i, null) >= LivenessListener.LIVENESS_DEAD) {
            logger.debug("Attempt to send message " + m + " to a dead node " + i + "!");

            if (deliverAckToMe != null) deliverAckToMe.sendFailed(handle, new NodeIsFaultyException(i));
        } else {
            if (simulator.isAlive(i)) {
                int delay = (int) Math.round(simulator.networkDelay(localIdentifier, i));
                handle.setSubCancellable(simulator.deliverMessage(m, i, localIdentifier, delay));
                if (deliverAckToMe != null) deliverAckToMe.ack(handle);
            } else {
                // drop message because the node is dead
            }
        }
        return handle;
    }

    public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<Identifier> handler) {
        this.errorHandler = handler;
    }

    public void destroy() {
        simulator.remove(getLocalIdentifier());
    }

    public boolean canReceiveSocket() {
        return acceptSockets;
    }

    public void finishReceiveSocket(P2PSocket<Identifier> acceptorEndpoint) {
        try {
            callback.incomingSocket(acceptorEndpoint);
        } catch (IOException ioe) {
            logger.warn("Exception in " + callback, ioe);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public synchronized int getNextSeq() {
        return seq++;
    }

    public void incomingMessage(Identifier i, MessageType m, Map<String, Object> options) throws IOException {
        callback.messageReceived(i, m, options);
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void clearState(Identifier i) {
        // do nothing
    }

    static class CancelAndClose<Identifier, MessageType> implements Cancellable {
        DirectAppSocket<Identifier, MessageType> closeMe;
        Cancellable cancelMe;

        public CancelAndClose(DirectAppSocket<Identifier, MessageType> socket, CancellableTask task) {
            this.closeMe = socket;
            this.cancelMe = task;
        }

        public boolean cancel() {
            closeMe.connectorEndpoint.close();
            return cancelMe.cancel();
        }
    }
}
