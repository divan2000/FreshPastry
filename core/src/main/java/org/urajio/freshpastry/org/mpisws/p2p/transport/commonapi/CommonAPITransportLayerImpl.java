package org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.QueueOverflowException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleInputBuffer;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class CommonAPITransportLayerImpl<Identifier extends NodeHandle> implements
        CommonAPITransportLayer<Identifier>,
        TransportLayerCallback<Identifier, ByteBuffer> {
    public static final String MSG_CLASS = "commonapi_msg_class";
    public static final String MSG_STRING = "commonapi_msg_string";
    public static final String MSG_TYPE = "commonapi_msg_type";
    public static final String MSG_ADDR = "commonapi_msg_addr";
    private final static Logger logger = LoggerFactory.getLogger(CommonAPITransportLayerImpl.class);
    protected OptionsAdder optionsAdder;
    protected boolean destroyed = false;
    TransportLayer<Identifier, ByteBuffer> tl;
    TransportLayerCallback<Identifier, RawMessage> callback;
    ErrorHandler<Identifier> errorHandler;
    RawMessageDeserializer deserializer;
    IdFactory idFactory;

    public CommonAPITransportLayerImpl(
            TransportLayer<Identifier, ByteBuffer> tl,
            IdFactory idFactory,
            RawMessageDeserializer deserializer,
            OptionsAdder optionsAdder,
            ErrorHandler<Identifier> errorHandler,
            Environment env) {

        this.tl = tl;
        this.deserializer = deserializer;
        this.optionsAdder = optionsAdder;
        if (this.optionsAdder == null) {
            this.optionsAdder = new OptionsAdder() {

                public Map<String, Object> addOptions(Map<String, Object> options,
                                                      RawMessage m) {
                    return OptionsFactory.addOption(options, MSG_STRING, m.toString(), MSG_TYPE, m.getType(), MSG_CLASS, m.getClass().getName());
                }
            };
        }

        if (tl == null) throw new IllegalArgumentException("tl must be non-null");
        if (idFactory == null) throw new IllegalArgumentException("idFactroy must be non-null");
        if (deserializer == null) throw new IllegalArgumentException("deserializer must be non-null");
        this.idFactory = idFactory;
        this.errorHandler = errorHandler;

        if (this.callback == null) {
            this.callback = new DefaultCallback<>();
        }
        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }


        tl.setCallback(this);
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

    public MessageRequestHandle<Identifier, RawMessage> sendMessage(
            final Identifier i,
            final RawMessage m,
            final MessageCallback<Identifier, RawMessage> deliverAckToMe,
            Map<String, Object> options) {
        if (destroyed) return null;

        logger.debug("sendMessage(" + i + "," + m + ")");

        final MessageRequestHandleImpl<Identifier, RawMessage> handle
                = new MessageRequestHandleImpl<>(i, m, options);
        final ByteBuffer buf;

        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        try {
            deserializer.serialize(m, sob);
        } catch (IOException ioe) {
            if (ioe instanceof NodeIsFaultyException) {
                ioe = new NodeIsFaultyException(i, m, ioe);
            }
            if (deliverAckToMe == null) {
                errorHandler.receivedException(i, ioe);
            } else {
                deliverAckToMe.sendFailed(handle, ioe);
            }
            return handle;
        }

        buf = ByteBuffer.wrap(sob.getBytes());
        logger.debug("sendMessage(" + i + "," + m + ") serizlized:" + buf);

        handle.setSubCancellable(tl.sendMessage(
                i,
                buf,
                new MessageCallback<Identifier, ByteBuffer>() {

                    public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
                        logger.debug("sendMessage(" + i + "," + m + ").ack()");
                        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable())
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                        if (deliverAckToMe != null) deliverAckToMe.ack(handle);
                    }

                    public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, Exception ex) {
                        if (ex instanceof NodeIsFaultyException) {
                            ex = new NodeIsFaultyException(i, m, ex);
                        }
                        if (ex instanceof QueueOverflowException) {
                            ex = new QueueOverflowException(i, m, ex);
                        }
                        logger.debug("sendFailed(" + i + "," + m + ")", ex);
                        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable())
                            throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                        if (deliverAckToMe == null) {
                            errorHandler.receivedException(i, ex);
                        } else {
                            deliverAckToMe.sendFailed(handle, ex);
                        }
                    }
                },
                optionsAdder.addOptions(options, m)));
        return handle;
    }

    public void messageReceived(Identifier i, ByteBuffer m, Map<String, Object> options) throws IOException {
        SimpleInputBuffer buf = new SimpleInputBuffer(m.array(), m.position());
        RawMessage ret = deserializer.deserialize(buf, i);
        logger.debug("messageReceived(" + i + "," + ret + ")");
        callback.messageReceived(i, ret, options);
    }

    public void setCallback(
            TransportLayerCallback<Identifier, RawMessage> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<Identifier> handler) {
        this.errorHandler = handler;
    }

    public void destroy() {
        destroyed = true;
        tl.destroy();
    }

    public SocketRequestHandle<Identifier> openSocket(
            final Identifier i,
            final SocketCallback<Identifier> deliverSocketToMe,
            Map<String, Object> options) {
        if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

        logger.debug("openSocket(" + i + ")");
        return tl.openSocket(i, deliverSocketToMe, options);
    }

    public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
        logger.debug("incomingSocket(" + s + ")");
        callback.incomingSocket(s);
    }
}
