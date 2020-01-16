package org.urajio.freshpastry.org.mpisws.p2p.transport.wire.magicnumber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.wire.exception.StalledSocketException;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * This class eliminates random connections by dropping all messages/sockets that don't
 * correlate to a specific header.  It is the simplest example of how to add a new
 * layer into the mpisws transport layer system.
 *
 * @author Jeff Hoye
 */
public class MagicNumberTransportLayer<Identifier> implements
        TransportLayer<Identifier, ByteBuffer>,
        TransportLayerCallback<Identifier, ByteBuffer>,
        ListenableTransportLayer<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(MagicNumberTransportLayer.class);
    // ******************************** TransportLayerListeners *******************************
    final ArrayList<TransportLayerListener<Identifier>> listeners = new ArrayList<>();
    public byte[] HEADER;
    public int SOCKET_TIMEOUT;
    protected Environment environment;
    protected TransportLayerCallback<Identifier, ByteBuffer> callback;
    protected ErrorHandler<Identifier> errorHandler;
    protected TransportLayer<Identifier, ByteBuffer> wire;

    /**
     * @param wtl
     * @param env
     * @param errorHandler call me when there is a problem
     * @param header       the header to expect from a socket/packet
     * @param timeOut      how long to wait before killing a socket that is not sending (milliseconds)
     */
    public MagicNumberTransportLayer(TransportLayer<Identifier, ByteBuffer> wtl,
                                     Environment env,
                                     ErrorHandler<Identifier> errorHandler,
                                     byte[] header,
                                     int timeOut) {
        this.environment = env;
        this.wire = wtl;
        this.HEADER = header;
        this.SOCKET_TIMEOUT = timeOut;

        this.errorHandler = errorHandler;

        this.callback = new DefaultCallback<>();

        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }

        wire.setCallback(this);
    }

    public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<Identifier> handler) {
        wire.setErrorHandler(handler);
        if (handler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
            return;
        }
        this.errorHandler = handler;
    }

    public void acceptMessages(boolean b) {
        wire.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        wire.acceptSockets(b);
    }

    public Identifier getLocalIdentifier() {
        return wire.getLocalIdentifier();
    }

    public SocketRequestHandle openSocket(final Identifier i,
                                          final SocketCallback<Identifier> deliverSocketToMe,
                                          Map<String, Object> options) {
        if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

        final SocketRequestHandleImpl<Identifier> cancellable = new SocketRequestHandleImpl<>(i, options);

        cancellable.setSubCancellable(wire.openSocket(i, new SocketCallback<Identifier>() {
            public void receiveResult(SocketRequestHandle<Identifier> c, final P2PSocket<Identifier> result) {
                if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable())
                    throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:" + c + " sub:" + cancellable.getSubCancellable());

                cancellable.setSubCancellable(new Cancellable() {
                    public boolean cancel() {
                        result.close();
                        return true;
                    }
                });

                result.register(false, true, new P2PSocketReceiver<Identifier>() {
                    ByteBuffer buf = ByteBuffer.wrap(HEADER);

                    public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
                        if (canRead) throw new IOException("Never asked to read!");
                        if (!canWrite) throw new IOException("Can't write!");
                        long ret = socket.write(buf);
                        if (ret < 0) {
                            socket.close();
                            return;
                        }
                        notifyListenersWrite((int) ret, socket.getIdentifier(), socket.getOptions(), false, true);
                        if (buf.hasRemaining()) {
                            socket.register(false, true, this);
                        } else {
                            deliverSocketToMe.receiveResult(cancellable, socket);
                        }
                    }

                    public void receiveException(P2PSocket<Identifier> socket, Exception e) {
                        deliverSocketToMe.receiveException(cancellable, e);
                    }
                });
            }

            public void receiveException(SocketRequestHandle<Identifier> c, Exception exception) {
                if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable())
                    throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:" + c + " sub:" + cancellable.getSubCancellable());
                deliverSocketToMe.receiveException(cancellable, exception);
//        errorHandler.receivedException(i, exception);
            }
        }, options));

        return cancellable;
    }

    public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(
            final Identifier i,
            final ByteBuffer m,
            final MessageCallback<Identifier, ByteBuffer> deliverAckToMe,
            Map<String, Object> options) {

        // build a new ByteBuffer with the header
        byte[] msgWithHeader = new byte[HEADER.length + m.remaining()];
        System.arraycopy(HEADER, 0, msgWithHeader, 0, HEADER.length);
        m.get(msgWithHeader, HEADER.length, m.remaining());

        logger.debug("sendMessage(" + i + "," + m + ")");

        final MessageRequestHandleImpl<Identifier, ByteBuffer> cancellable
                = new MessageRequestHandleImpl<>(i, m, options);

        final ByteBuffer buf = ByteBuffer.wrap(msgWithHeader);
        cancellable.setSubCancellable(wire.sendMessage(i,
                buf,
                new MessageCallback<Identifier, ByteBuffer>() {

                    public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
                        if (cancellable.getSubCancellable() != null && msg != cancellable.getSubCancellable())
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + cancellable.getSubCancellable());
                        if (deliverAckToMe != null) deliverAckToMe.ack(cancellable);
                        notifyListenersWrite(HEADER.length, i, cancellable.getOptions(), false, false);  // non-pasthrough part
                        notifyListenersWrite(buf.limit() - HEADER.length, i, cancellable.getOptions(), true, false); // passthrough part
                    }

                    public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, Exception ex) {
                        if (cancellable.getSubCancellable() != null && msg != cancellable.getSubCancellable())
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + cancellable.getSubCancellable());
                        if (deliverAckToMe == null) {
                            errorHandler.receivedException(i, ex);
                        } else {
                            deliverAckToMe.sendFailed(cancellable, ex);
                        }
                    }
                },
                options));

        return cancellable;
    }

    public void destroy() {
        wire.destroy();
    }

    public void incomingSocket(P2PSocket<Identifier> s) {
        s.register(true, false, new VerifyHeaderReceiver(s));
    }

    public void messageReceived(Identifier i, ByteBuffer m, Map<String, Object> options) throws IOException {
        logger.debug("messageReceived(" + i + "," + m + ")");

        if (m.remaining() < HEADER.length) {
            errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
            return;
        }
        byte[] hdr = new byte[HEADER.length];

        m.get(hdr);

        int remaining = m.remaining();
        if (Arrays.equals(HEADER, hdr)) {
            notifyListenersRead(HEADER.length, i, options, false, false);  // non-pasthrough part
            notifyListenersRead(remaining, i, options, true, false); // passthrough part
            callback.messageReceived(i, m, options);
            return;
        }

        notifyListenersRead(HEADER.length, i, options, false, false);  // non-pasthrough part
        notifyListenersRead(remaining, i, options, true, false); // passthrough part

        errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
    }

    public void addTransportLayerListener(
            TransportLayerListener<Identifier> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeTransportLayerListener(
            TransportLayerListener<Identifier> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void notifyListenersRead(int bytesRead, Identifier identifier,
                                    Map<String, Object> options, boolean passthrough, boolean socket) {
        Iterable<TransportLayerListener<Identifier>> i;
        synchronized (listeners) {
            i = new ArrayList<>(listeners);
        }
        for (TransportLayerListener<Identifier> l : i) {
            l.read(bytesRead, identifier, options, passthrough, socket);
        }
    }

    public void notifyListenersWrite(int bytesRead, Identifier identifier,
                                     Map<String, Object> options, boolean passthrough, boolean socket) {
        Iterable<TransportLayerListener<Identifier>> i;
        synchronized (listeners) {
            i = new ArrayList<>(listeners);
        }
        for (TransportLayerListener<Identifier> l : i) {
            l.wrote(bytesRead, identifier, options, passthrough, socket);
        }
    }

    protected class VerifyHeaderReceiver extends TimerTask implements P2PSocketReceiver<Identifier> {
        ByteBuffer buf = ByteBuffer.allocate(HEADER.length);

        P2PSocket<Identifier> socket;

        public VerifyHeaderReceiver(P2PSocket<Identifier> s) {
            this.socket = s;
            environment.getSelectorManager().getTimer().schedule(this, SOCKET_TIMEOUT);
        }

        public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
            errorHandler.receivedException(socket.getIdentifier(), ioe);
            // TODO Auto-generated method stub
        }

        public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
            // TODO: Optimization: Check array at each step, to fail faster
            // TODO: Make timeout/cancellable
            if (canWrite) throw new IOException("Never asked to write!");
            if (!canRead) throw new IOException("Can't read!");
            long bytesRead;
            if ((bytesRead = socket.read(buf)) < 0) {
                socket.close();
                return;
            }
            notifyListenersRead((int) bytesRead, socket.getIdentifier(), socket.getOptions(), false, true);
            if (buf.hasRemaining()) {
                socket.register(true, false, this);
            } else {
                if (Arrays.equals(HEADER, buf.array())) {
                    // header matched
                    cancel();
                    callback.incomingSocket(socket);
                } else {
                    cancel();
                    errorHandler.receivedUnexpectedData(socket.getIdentifier(), buf.array(), 0, null);
                    socket.close();
                }
            }
        }

        @Override
        public void run() {
            socket.close();
            errorHandler.receivedException(socket.getIdentifier(), new StalledSocketException(socket.getIdentifier(), "Timeout on incoming socket expired."));
        }

        public String toString() {
            return MagicNumberTransportLayer.this + " VHR";
        }
    }
}
