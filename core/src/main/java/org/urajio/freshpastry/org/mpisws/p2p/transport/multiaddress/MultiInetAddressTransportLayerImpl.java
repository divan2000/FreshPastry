package org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.*;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleInputBuffer;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * This class adds an epoch and a list of InetSocketAddresses, and also disambiguates between them
 * for the lower layer.  This is useful in situations where the node is behind a NAT and is addressed
 * differently for nodes outside the NAT as inside.
 * <p>
 * <p>
 * Optimization: Pre-serialize localAddress in the ctor, then just mem-copy it to the buffer
 *
 * @author Jeff Hoye
 */
public class MultiInetAddressTransportLayerImpl implements MultiInetAddressTransportLayer, TransportLayerCallback<InetSocketAddress, ByteBuffer> {
    private final static Logger logger = LoggerFactory.getLogger(MultiInetAddressTransportLayerImpl.class);

    /**
     * The max addresses in an EpochInetSocketAddress
     */
    int MAX_NUM_ADDRESSES;
    TransportLayer<InetSocketAddress, ByteBuffer> wire;
    MultiInetSocketAddress localAddress;
    TransportLayerCallback<MultiInetSocketAddress, ByteBuffer> callback;
    ErrorHandler<MultiInetSocketAddress> errorHandler;
    AddressStrategy strategy;

    private boolean sendIdentifier = true;

    public MultiInetAddressTransportLayerImpl(
            MultiInetSocketAddress localAddress,
            TransportLayer<InetSocketAddress, ByteBuffer> wire,
            Environment env,
            ErrorHandler<MultiInetSocketAddress> handler,
            AddressStrategy strategy) {
        this.wire = wire;
        this.errorHandler = handler;
        this.localAddress = localAddress;
        this.strategy = strategy;
        MAX_NUM_ADDRESSES = env.getParameters().getInt("transport_epoch_max_num_addresses");
        if (wire == null)
            throw new IllegalArgumentException("TransportLayer<InetSocketAddress, ByteBuffer> wire must be non-null");
        if (localAddress == null)
            throw new IllegalArgumentException("EpochInetSocketAddress localAddress must be non-null");

        this.callback = new DefaultCallback<>();

        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }
        if (this.strategy == null) {
            this.strategy = new SimpleAddressStrategy();
        }

        wire.setCallback(this);
    }

    public AddressStrategy getAddressStrategy() {
        return strategy;
    }

    public SocketRequestHandle<MultiInetSocketAddress> openSocket(
            final MultiInetSocketAddress i,
            final SocketCallback<MultiInetSocketAddress> deliverSocketToMe,
            Map<String, Object> options) {

        if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

        final SocketRequestHandleImpl<MultiInetSocketAddress> handle = new SocketRequestHandleImpl<>(i, options);

        logger.info("openSocket(" + i + "," + deliverSocketToMe + "," + options + ")");
        SimpleOutputBuffer sob = new SimpleOutputBuffer(localAddress.getSerializedLength());
        try {
            localAddress.serialize(sob);
        } catch (IOException ioe) {
            deliverSocketToMe.receiveException(handle, ioe);
            return null;
        }
        final ByteBuffer b = sendIdentifier ? sob.getByteBuffer() : null;

        InetSocketAddress addr = strategy.getAddress(getLocalIdentifier(), i);

        handle.setSubCancellable(wire.openSocket(addr,
                new SocketCallback<>() {

                    public void receiveResult(SocketRequestHandle<InetSocketAddress> c, final P2PSocket<InetSocketAddress> result) {
                        if (handle.getSubCancellable() != null && c != handle.getSubCancellable())
                            throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:" + c + " sub:" + handle.getSubCancellable());

                        handle.setSubCancellable(new Cancellable() {
                            public boolean cancel() {
                                result.close();
                                return true;
                            }
                        });

                        logger.debug("openSocket(" + i + "):receiveResult(" + result + ")");
                        if (sendIdentifier) {
                            result.register(false, true, new P2PSocketReceiver<>() {
                                public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
                                                                boolean canRead, boolean canWrite) throws IOException {
                                    if (canRead || !canWrite)
                                        throw new IOException("Expected to write! " + canRead + "," + canWrite);

                                    // do the work
                                    if (socket.write(b) < 0) {
                                        deliverSocketToMe.receiveException(handle, new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                                        return;
                                    }

                                    // keep working or pass up the new socket
                                    if (b.hasRemaining()) {
                                        socket.register(false, true, this);
                                    } else {
                                        deliverSocketToMe.receiveResult(handle, new SocketWrapperSocket<>(i, socket, errorHandler, socket.getOptions()));
                                    }
                                }

                                public void receiveException(P2PSocket<InetSocketAddress> socket,
                                                             Exception e) {
                                    deliverSocketToMe.receiveException(handle, e);
                                }
                            });

                        } else {
                            deliverSocketToMe.receiveResult(handle, new SocketWrapperSocket<>(i, result, errorHandler, result.getOptions()));
                        }
                    }

                    public void receiveException(SocketRequestHandle<InetSocketAddress> c, Exception exception) {
                        if (handle.getSubCancellable() != null && c != handle.getSubCancellable()) {
                            throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:" + c + " sub:" + handle.getSubCancellable());
                        }
                        deliverSocketToMe.receiveException(handle, exception);
                    }
                }, options));
        return handle;
    }

    public void incomingSocket(P2PSocket<InetSocketAddress> s) throws IOException {
        logger.debug("incomingSocket(" + s + "):" + sendIdentifier);

        if (sendIdentifier) {
            final SocketInputBuffer sib = new SocketInputBuffer(s, 1024);
            s.register(true, false, new P2PSocketReceiver<>() {

                public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
                                                boolean canRead, boolean canWrite) throws IOException {
                    logger.debug("incomingSocket(" + socket + "):receiveSelectResult()");
                    if (canWrite || !canRead) throw new IOException("Expected to read! " + canRead + "," + canWrite);
                    try {
                        MultiInetSocketAddress eisa = MultiInetSocketAddress.build(sib);
                        logger.debug("Read " + eisa);
                        callback.incomingSocket(new SocketWrapperSocket<>(eisa, socket, errorHandler, socket.getOptions()));
                    } catch (InsufficientBytesException ibe) {
                        socket.register(true, false, this);
                    } catch (IOException e) {
                        errorHandler.receivedException(new MultiInetSocketAddress(socket.getIdentifier()), e);
                    }
                }

                public void receiveException(P2PSocket<InetSocketAddress> socket, Exception e) {
                    errorHandler.receivedException(new MultiInetSocketAddress(socket.getIdentifier()), e);
                }
            });

        } else {
            // just pass up the socket
            callback.incomingSocket(
                    new SocketWrapperSocket<>(
                            new MultiInetSocketAddress(s.getIdentifier()), s, errorHandler, s.getOptions()));
        }
    }

    public MessageRequestHandle<MultiInetSocketAddress, ByteBuffer> sendMessage(
            final MultiInetSocketAddress i,
            final ByteBuffer m,
            final MessageCallback<MultiInetSocketAddress, ByteBuffer> deliverAckToMe,
            Map<String, Object> options) {

        logger.debug("sendMessage(" + i + "," + m + ")");

        final MessageRequestHandleImpl<MultiInetSocketAddress, ByteBuffer> handle
                = new MessageRequestHandleImpl<>(i, m, options);
        final ByteBuffer buf;
        if (sendIdentifier) {
            SimpleOutputBuffer sob = new SimpleOutputBuffer(m.remaining() + localAddress.getSerializedLength());
            try {
                localAddress.serialize(sob);
                sob.write(m.array(), m.position(), m.remaining());
            } catch (IOException ioe) {
                if (deliverAckToMe == null) {
                    errorHandler.receivedException(i, ioe);
                } else {
                    deliverAckToMe.sendFailed(handle, ioe);
                }
                return handle;
            }
            buf = ByteBuffer.wrap(sob.getBytes());
        } else {
            buf = m;
        }

        handle.setSubCancellable(wire.sendMessage(
                strategy.getAddress(getLocalIdentifier(), i),
                buf,
                new MessageCallback<>() {

                    public void ack(MessageRequestHandle<InetSocketAddress, ByteBuffer> msg) {
                        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) {
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                        }
                        if (deliverAckToMe != null) {
                            deliverAckToMe.ack(handle);
                        }
                    }

                    public void sendFailed(MessageRequestHandle<InetSocketAddress, ByteBuffer> msg, Exception ex) {
                        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable())
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                        if (deliverAckToMe == null) {
                            if (ex instanceof NodeIsFaultyException) return;
                            errorHandler.receivedException(i, ex);
                        } else {
                            deliverAckToMe.sendFailed(handle, ex);
                        }
                    }
                },
                options));
        return handle;
    }

    public String toString() {
        return "MultiInetAddrTL{" + localAddress + "}";
    }

    public void messageReceived(InetSocketAddress i, ByteBuffer m, Map<String, Object> options) throws IOException {
        logger.debug("messageReceived(" + i + "," + m + ")");
        // read numAddresses
        if (sendIdentifier) {
            int pos = m.position();
            SimpleInputBuffer sib = new SimpleInputBuffer(m.array(), pos);

            MultiInetSocketAddress eisa;
            try {
                eisa = MultiInetSocketAddress.build(sib);
            } catch (IOException ioe) {
                errorHandler.receivedUnexpectedData(new MultiInetSocketAddress(i), m.array(), pos, null);
                return;
            }

            // make sure to leave m at the proper position
            m.position(m.array().length - sib.bytesRemaining());

            callback.messageReceived(eisa, m, options);
        } else {
            callback.messageReceived(new MultiInetSocketAddress(i), m, options);
        }
    }

    public MultiInetSocketAddress getLocalIdentifier() {
        return localAddress;
    }

    public void acceptMessages(boolean b) {
        wire.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        wire.acceptSockets(b);
    }

    public void destroy() {
        wire.destroy();
    }

    public void setCallback(TransportLayerCallback<MultiInetSocketAddress, ByteBuffer> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<MultiInetSocketAddress> handler) {
        this.errorHandler = handler;
    }

    public boolean isSendIdentifier() {
        return sendIdentifier;
    }

    /**
     * Set this to false to prevent sending/receiving the identifier at this layer for the
     * message/socket.  Note that identity will return a guess based
     * on the ipaddress of the message/socket, and the epoch will be UNKNOWN
     * Only use this if a higher layer will do the work of identification.
     *
     * @param sendIdentifier
     */
    public void setSendIdentifier(boolean sendIdentifier) {
        this.sendIdentifier = sendIdentifier;
    }
}
