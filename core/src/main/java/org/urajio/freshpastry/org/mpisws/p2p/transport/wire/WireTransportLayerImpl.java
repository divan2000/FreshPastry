package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class WireTransportLayerImpl implements WireTransportLayer, ListenableTransportLayer<InetSocketAddress>, SocketOpeningTransportLayer<InetSocketAddress> {
    private final static Logger logger = LoggerFactory.getLogger(WireTransportLayerImpl.class);
    final Collection<TransportLayerListener<InetSocketAddress>> listeners =
            new ArrayList<>();
    final Collection<SocketCountListener<InetSocketAddress>> slisteners =
            new ArrayList<>();
    /**
     * true for modelnet, who needs to set the bind address even on outgoing sockets
     */
    public boolean forceBindAddress = false;
    // state
    protected InetSocketAddress bindAddress;
    // helpers
    protected UDPLayer udp;
    protected TCPLayer tcp;
    // abstract helpers
    protected Environment environment;
    protected ErrorHandler<InetSocketAddress> errorHandler;
    boolean destroyed = false;
    private TransportLayerCallback<InetSocketAddress, ByteBuffer> callback;

    /**
     * @param bindAddress the address to bind to, if it fails, it will throw an exception
     * @param env         will acquire the SelectorManager from the env
     * @throws IOException
     */
    public WireTransportLayerImpl(
            InetSocketAddress bindAddress,
            Environment env,
            ErrorHandler<InetSocketAddress> errorHandler) throws IOException {
        this(bindAddress, env, errorHandler, true);
    }

    public WireTransportLayerImpl(
            InetSocketAddress bindAddress,
            Environment env,
            ErrorHandler<InetSocketAddress> errorHandler, boolean enableServer) throws IOException {
        this(bindAddress, env, errorHandler, enableServer, enableServer);
    }

    public WireTransportLayerImpl(
            InetSocketAddress bindAddress,
            Environment env,
            ErrorHandler<InetSocketAddress> errorHandler, boolean enableTCPServer, boolean enableUDPServer) throws IOException {
        this.bindAddress = bindAddress;
        this.environment = env;

        Parameters p = this.environment.getParameters();
        if (p.contains("wire_forceBindAddress")) {
            forceBindAddress = p.getBoolean("wire_forceBindAddress");
        }

        this.callback = new DefaultCallback<>();
        this.errorHandler = errorHandler;

        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }

        if (enableUDPServer) {
            udp = new UDPLayerImpl(this);
        } else {
            udp = new BogusUDPLayerImpl();
        }
        try {
            tcp = new TCPLayer(this, enableTCPServer);
        } catch (IOException ioe) {
            udp.destroy();
            throw ioe;
        }
    }

    public void setCallback(TransportLayerCallback<InetSocketAddress, ByteBuffer> callback) {
        this.callback = callback;
    }

    public SocketRequestHandle<InetSocketAddress> openSocket(InetSocketAddress destination, SocketCallback<InetSocketAddress> deliverSocketToMe, Map<String, Object> options) {
        return tcp.openSocket(destination, deliverSocketToMe, options);
    }

    public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
            InetSocketAddress destination,
            ByteBuffer m,
            MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe,
            Map<String, Object> options) {
        logger.debug("sendMessage(" + destination + "," + m + ")");
        return udp.sendMessage(destination, m, deliverAckToMe, options);
    }

    public InetSocketAddress getLocalIdentifier() {
        return bindAddress;
    }

    public void destroy() {
        logger.info("destroy()");
        destroyed = true;
        udp.destroy();
        tcp.destroy();
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void setErrorHandler(ErrorHandler<InetSocketAddress> handler) {
        if (handler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
            return;
        }
        this.errorHandler = handler;
    }

    public void acceptMessages(boolean b) {
        udp.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        tcp.acceptSockets(b);
    }

    protected void messageReceived(InetSocketAddress address, ByteBuffer buffer, Map<String, Object> options) throws IOException {
        callback.messageReceived(address, buffer, options);
    }

    protected void incomingSocket(P2PSocket<InetSocketAddress> sm) throws IOException {
        broadcastChannelOpened(sm.getIdentifier(), sm.getOptions(), false);
        callback.incomingSocket(sm);
    }

    public void addTransportLayerListener(
            TransportLayerListener<InetSocketAddress> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeTransportLayerListener(
            TransportLayerListener<InetSocketAddress> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    protected Iterable<TransportLayerListener<InetSocketAddress>> getTLlisteners() {
        synchronized (listeners) {
            return new ArrayList<>(listeners);
        }
    }

    public void addSocketCountListener(
            SocketCountListener<InetSocketAddress> listener) {
        synchronized (slisteners) {
            slisteners.add(listener);
        }
    }

    public void removeSocketCountListener(
            SocketCountListener<InetSocketAddress> listener) {
        synchronized (slisteners) {
            slisteners.remove(listener);
        }
    }

    protected Iterable<SocketCountListener<InetSocketAddress>> getSlisteners() {
        synchronized (slisteners) {
            return new ArrayList<>(slisteners);
        }
    }

    public void broadcastChannelOpened(InetSocketAddress addr, Map<String, Object> options, boolean outgoing) {
        for (SocketCountListener<InetSocketAddress> listener : getSlisteners())
            listener.socketOpened(addr, options, outgoing);
    }

    public void broadcastChannelClosed(InetSocketAddress addr, Map<String, Object> options) {
        for (SocketCountListener<InetSocketAddress> listener : getSlisteners())
            listener.socketClosed(addr, options);
    }

    public void notifyRead(long bytes, InetSocketAddress addr, boolean tcp) {
        synchronized (listeners) {
            for (TransportLayerListener<InetSocketAddress> l : listeners) {
                l.read((int) bytes, addr, null, true, tcp);
            }
        }
    }

    public void notifyWrite(long bytes, InetSocketAddress addr, boolean tcp) {
        synchronized (listeners) {
            for (TransportLayerListener<InetSocketAddress> l : listeners) {
                l.wrote((int) bytes, addr, null, true, tcp);
            }
        }
    }
}
