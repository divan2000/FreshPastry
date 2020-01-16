package org.urajio.freshpastry.org.mpisws.p2p.transport.limitsockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketWrapperSocket;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.selector.Timer;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Automatically closes sockets based on LRU.
 * <p>
 * Uses the LinkedHashMap to perfrom the LRU policy.
 *
 * @author Jeff Hoye
 */
public class LimitSocketsTransportLayer<Identifier, MessageType> implements
        TransportLayer<Identifier, MessageType>,
        TransportLayerCallback<Identifier, MessageType> {
    private final static Logger logger = LoggerFactory.getLogger(LimitSocketsTransportLayer.class);
    protected final LinkedHashMap<LSSocket, LSSocket> cache;
    protected TransportLayer<Identifier, MessageType> tl;
    protected TransportLayerCallback<Identifier, MessageType> callback;
    protected Timer timer;
    protected ErrorHandler<Identifier> handler;
    int MAX_SOCKETS;

    public LimitSocketsTransportLayer(int max_sockets, TransportLayer<Identifier, MessageType> tl, ErrorHandler<Identifier> handler, Environment env) {
        this.MAX_SOCKETS = max_sockets;
        this.tl = tl;
        this.timer = env.getSelectorManager().getTimer();
        this.cache = new LinkedHashMap<>(MAX_SOCKETS, 0.75f, true);
        this.handler = handler;
        if (this.handler == null) {
            this.handler = new DefaultErrorHandler<>();
        }

        tl.setCallback(this);
    }

    public SocketRequestHandle<Identifier> openSocket(final Identifier i, final SocketCallback<Identifier> deliverSocketToMe, final Map<String, Object> options) {
        logger.debug(LimitSocketsTransportLayer.this + ".openSocket(" + i + "," + deliverSocketToMe + "," + options + ")", new Exception("Stack Trace"));

        final SocketRequestHandleImpl<Identifier> ret = new SocketRequestHandleImpl<Identifier>(i, options) {
            @Override
            public boolean cancel() {
                logger.debug(this + ".openSocket(" + i + "," + deliverSocketToMe + "):" + this + ".cancel()");
                return super.cancel();
            }

            public String toString() {
                return LimitSocketsTransportLayer.this + "RequestHandle.openSocket(" + i + "," + deliverSocketToMe + "," + options + ")";
            }
        };

        ret.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {
            public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
                logger.debug(this + ".openSocket(" + i + "," + deliverSocketToMe + "):" + ret + ".receiveResult()");
                deliverSocketToMe.receiveResult(ret, getLSSock(sock));
            }

            public void receiveException(SocketRequestHandle<Identifier> s, Exception ex) {
                logger.debug(this + ".openSocket(" + i + "," + deliverSocketToMe + "):" + ret + ".receiveException(" + ex + ")");
                deliverSocketToMe.receiveException(ret, ex);
            }

            public String toString() {
                return LimitSocketsTransportLayer.this + "SocketCallback.openSocket(" + i + "," + deliverSocketToMe + "," + options + ")";
            }

        }, options));

        return ret;
    }

    public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
        logger.debug(this + ".incomingSocket(" + s + ")");
        callback.incomingSocket(getLSSock(s));
    }

    protected LSSocket getLSSock(P2PSocket<Identifier> sock) {
        LSSocket ret = new LSSocket(sock);
        cache.put(ret, ret);
        closeIfNecessary();
        return ret;
    }

    protected void closeIfNecessary() {
        Collection<LSSocket> closeMe = new ArrayList<>();
        synchronized (cache) {
            while (cache.size() > MAX_SOCKETS) {
                Iterator<LSSocket> i = cache.keySet().iterator();
                closeMe.add(i.next());
                i.remove();
            }
        }
        for (LSSocket sock : closeMe) {
            sock.forceClose();
        }
    }

    public void touch(LSSocket socket) {
        synchronized (cache) {
            if (cache.get(socket) == null) {
                cache.put(socket, socket);
                closeIfNecessary();
            }
        }
    }

    public String toString() {
        return "LimitSocks<" + cache.size() + ">";
    }

    public void acceptMessages(boolean b) {
        tl.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        tl.acceptSockets(b);
    }

    public Identifier getLocalIdentifier() {
        return tl.getLocalIdentifier();
    }

    public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Object> options) {
        return tl.sendMessage(i, m, deliverAckToMe, options);
    }

    public void messageReceived(Identifier i, MessageType m, Map<String, Object> options) throws IOException {
        callback.messageReceived(i, m, options);
    }

    public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<Identifier> handler) {
        this.handler = handler;
        tl.setErrorHandler(handler);
    }

    public void destroy() {
        tl.destroy();
    }

    class LSSocket extends SocketWrapperSocket<Identifier, Identifier> {
        private final Logger logger = LoggerFactory.getLogger(LSSocket.class);

        boolean closed = false;
        boolean forcedClose = false;

        public LSSocket(P2PSocket<Identifier> socket) {
            super(socket.getIdentifier(), socket, handler, socket.getOptions());
        }

        /**
         * Called when we force a socket closed.
         */
        public void forceClose() {
//      logger.log(this+".forceClose()");
            logger.debug(this + ".forceClose()");
            forcedClose = true;
            super.shutdownOutput();
//      super.close();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        close();
                    } catch (Exception ioe) {
                        // do nothing, it's probably already closed anyway
                    }
                }
            }, 3000);
        }

        /**
         * Called by the higher layer
         */
        @Override
        public void close() {
            logger.debug(this + ".close()");
            closed = true;
            cache.remove(this);
            super.close();
        }

        @Override
        public long read(ByteBuffer dsts) throws IOException {
            if (!closed) touch(this);
            try {
                return super.read(dsts);
            } catch (IOException ioe) {
                close();
                throw ioe;
            }
        }

        @Override
        public void register(boolean wantToRead, boolean wantToWrite, P2PSocketReceiver<Identifier> receiver) {
            if (forcedClose) {
                if (wantToWrite) {
                    receiver.receiveException(this, new ClosedChannelException("Limit Sockets forced close. " + this));
                }
                if (wantToRead) {
                    super.register(true, false, receiver);
                }
                return;
            }
            if (!closed) touch(this);
            super.register(wantToRead, wantToWrite, receiver);
        }

        @Override
        public long write(ByteBuffer srcs) throws IOException {
            if (forcedClose) throw new ClosedChannelException("Limit Sockets forced close. " + this);
            if (!closed) touch(this);
            try {
                return super.write(srcs);
            } catch (IOException ioe) {
                close();
                throw ioe;
            }
        }

        @Override
        public String toString() {
            return LimitSocketsTransportLayer.this.toString() + "$LSSocket<" + identifier + ">[" + (closed ? "closed" : "open") + "]@" + System.identityHashCode(this) + socket.toString();
        }
    }


}
