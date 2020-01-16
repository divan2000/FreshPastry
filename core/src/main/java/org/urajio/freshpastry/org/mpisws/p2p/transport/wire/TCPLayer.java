package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.selector.SelectionKeyHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.*;

public class TCPLayer extends SelectionKeyHandler {
    public static final Map<String, Object> OPTIONS;
    private final static Logger logger = LoggerFactory.getLogger(TCPLayer.class);

    static {
        Map<String, Object> map = new HashMap<>();
        map.put(WireTransportLayer.OPTION_TRANSPORT_TYPE, WireTransportLayer.TRANSPORT_TYPE_GUARANTEED);
        OPTIONS = Collections.unmodifiableMap(map);
    }

    // the number of sockets where we start closing other sockets
    public final int MAX_OPEN_SOCKETS;

    // the size of the buffers for the socket
    public final int SOCKET_BUFFER_SIZE;
    final Collection<SocketManager> sockets = new HashSet<>();
    public boolean TCP_NO_DELAY = false;
    WireTransportLayerImpl wire;
    // the key to accept from
    private SelectionKey key;
    // the buffer used to read the header
    private ByteBuffer buffer;

    public TCPLayer(WireTransportLayerImpl wire, boolean enableServer) throws IOException {
        this.wire = wire;

        Parameters p = wire.environment.getParameters();
        MAX_OPEN_SOCKETS = p.getInt("pastry_socket_scm_max_open_sockets");
        SOCKET_BUFFER_SIZE = p.getInt("pastry_socket_scm_socket_buffer_size"); // 32768
        if (p.contains("transport_tcp_no_delay")) {
            TCP_NO_DELAY = p.getBoolean("transport_tcp_no_delay");
        }
        ServerSocketChannel temp = null; // just to clean up after the exception

        // bind to port
        if (enableServer) {
            final ServerSocketChannel channel = ServerSocketChannel.open();
            temp = channel;
            channel.configureBlocking(false);
            channel.socket().setReuseAddress(true);
            channel.socket().bind(wire.bindAddress);
            logger.info("TCPLayer bound to " + wire.bindAddress);

            this.key = wire.environment.getSelectorManager().register(channel, this, SelectionKey.OP_ACCEPT);
        }
    }

    public SocketRequestHandle<InetSocketAddress> openSocket(
            InetSocketAddress destination,
            SocketCallback<InetSocketAddress> deliverSocketToMe,
            Map<String, Object> options) {
        if (isDestroyed()) {
            return null;
        }
        logger.debug("openSocket(" + destination + ")", new Exception("Stack Trace"));

        if (deliverSocketToMe == null) {
            throw new IllegalArgumentException("deliverSocketToMe must be non-null!");
        }
        try {
            wire.broadcastChannelOpened(destination, options, true);

            synchronized (sockets) {
                SocketManager sm = new SocketManager(this, destination, deliverSocketToMe, options);
                sockets.add(sm);
                return sm;
            }
        } catch (IOException e) {
            logger.warn("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + destination + " AS DEAD!", e);
            SocketRequestHandle<InetSocketAddress> can = new SocketRequestHandleImpl<>(destination, options);
            deliverSocketToMe.receiveException(can, e);
            return can;
        }
    }

    protected void socketClosed(SocketManager sm) {
        wire.broadcastChannelClosed(sm.addr, sm.options);
        sockets.remove(sm);
    }

    public void destroy() {
        logger.info("destroy()");

        try {
            if (key != null) {
                key.channel().close();
                key.cancel();
                key.attach(null);
            }
        } catch (IOException ioe) {
            wire.errorHandler.receivedException(null, ioe);
        }

        // TODO: add a flag to disable this to simulate a silent fault
        for (SocketManager socket : new ArrayList<>(sockets)) {
            socket.close();
        }
    }

    public void acceptSockets(final boolean b) {
        Runnable r = new Runnable() {
            public void run() {
                if (b) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT);
                } else {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);
                }
            }
        };

        // thread safety
        if (wire.environment.getSelectorManager().isSelectorThread()) {
            r.run();
        } else {
            wire.environment.getSelectorManager().invoke(r);
        }
    }

    /**
     * Specified by the SelectionKeyHandler interface. Is called whenever a key
     * has become acceptable, representing an incoming connection. This method
     * will accept the connection, and attach a SocketConnector in order to read
     * the greeting off of the channel. Once the greeting has been read, the
     * connector will hand the channel off to the appropriate node handle.
     *
     * @param key The key which is acceptable.
     */
    public void accept(SelectionKey key) {
        try {
            SocketManager sm = new SocketManager(this, key);
            synchronized (sockets) {
                sockets.add(sm);
            }
            wire.incomingSocket(sm);
        } catch (IOException e) {
            logger.warn("ERROR (accepting connection): " + e);
            wire.errorHandler.receivedException(null, e);
        }
    }

    public boolean isDestroyed() {
        return wire.isDestroyed();
    }

    public void notifyRead(long ret, InetSocketAddress addr) {
        wire.notifyRead(ret, addr, true);
    }

    public void notifyWrite(long ret, InetSocketAddress addr) {
        wire.notifyWrite(ret, addr, true);
    }
}
