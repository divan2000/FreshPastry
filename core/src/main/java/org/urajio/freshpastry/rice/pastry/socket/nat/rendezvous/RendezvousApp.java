package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.wire.WireTransportLayer;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.util.AttachableCancellable;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;
import org.urajio.freshpastry.rice.selector.SelectorManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * TODO: make not abstract
 *
 * @author Jeff Hoye
 */
public class RendezvousApp extends PastryAppl implements RendezvousStrategy<RendezvousSocketNodeHandle> {
    private final static Logger logger = LoggerFactory.getLogger(RendezvousApp.class);

    protected LeafSet leafSet;
    protected SelectorManager selectorManager;
    protected RendezvousTransportLayer<RendezvousSocketNodeHandle> tl;


    public RendezvousApp(PastryNode pn) {
        super(pn, null, 0, null);
        setDeserializer(new MessageDeserializer() {

            public org.urajio.freshpastry.rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type,
                                                                                 int priority, NodeHandle sender) throws IOException {
                byte version;
                switch (type) {
                    case ByteBufferMsg.TYPE:
                        version = buf.readByte();
                        if (version == 0) { // version 0
                            NodeHandle originalSender = thePastryNode.readNodeHandle(buf);
                            int length = buf.readInt();
                            byte[] msg = new byte[length];
                            buf.read(msg);
                            return new ByteBufferMsg(ByteBuffer.wrap(msg), originalSender, priority, getAddress());
                        } else {
                            throw new IllegalArgumentException("Unknown version for ByteBufferMsg: " + version);
                        }
                    case PilotForwardMsg.TYPE:
                        version = buf.readByte();
                        if (version == 0) { // version 0
                            RendezvousSocketNodeHandle target = (RendezvousSocketNodeHandle) thePastryNode.readNodeHandle(buf);
                            ByteBufferMsg subMsg = (ByteBufferMsg) deserialize(buf, ByteBufferMsg.TYPE, priority, sender);
                            return new PilotForwardMsg(getAddress(), subMsg, target);
                        } else {
                            throw new IllegalArgumentException("Unknown version for PilotForwardMsg: " + version);
                        }
                    case OpenChannelMsg.TYPE:
                        version = buf.readByte();
                        if (version == 0) { // version 0
                            RendezvousSocketNodeHandle rendezvous = (RendezvousSocketNodeHandle) thePastryNode.readNodeHandle(buf);
                            RendezvousSocketNodeHandle source = (RendezvousSocketNodeHandle) thePastryNode.readNodeHandle(buf);
                            int uid = buf.readInt();
                            return new OpenChannelMsg(getAddress(), rendezvous, source, uid);
                        } else {
                            throw new IllegalArgumentException("Unknown version for PilotForwardMsg: " + version);
                        }
                    default:
                        throw new IllegalArgumentException("Unknown type: " + type);
                }
            }
        }); // this constructor doesn't auto-register
        leafSet = pn.getLeafSet();
        selectorManager = pn.getEnvironment().getSelectorManager();
    }

    /**
     * Can be called before you boot, will tell you if you are Firewalled.
     * Should send a message to the bootstrap, who forwards it to another node who sends you the request back.  Should
     * try UDP/TCP.
     * <p>
     * Returns your external address.
     *
     * @param bootstrap
     * @param receiveResult
     */
    public void isNatted(NodeHandle bootstrap, Continuation<InetSocketAddress, Exception> receiveResult) {
    }

    @Override
    public void messageForAppl(Message msg) {
        if (msg instanceof ByteBufferMsg) {
            ByteBufferMsg bbm = (ByteBufferMsg) msg;
            logger.debug("messageForAppl(" + bbm + ")");
            try {
                tl.messageReceivedFromOverlay((RendezvousSocketNodeHandle) bbm.getOriginalSender(), bbm.buffer, null);
            } catch (IOException ioe) {
                logger.warn("dropping " + bbm, ioe);
            }
            // TODO: Get a reference to the TL... is this an interface?  Should it be?
            // TODO: Deliver this to the TL.

            //    tl.messageReceived();
            //    throw new RuntimeException("Not implemented.");
            return;
        }
        if (msg instanceof PilotForwardMsg) {
            PilotForwardMsg pfm = (PilotForwardMsg) msg;
            logger.debug("Forwarding message " + pfm);
            thePastryNode.send(pfm.getTarget(), pfm.getBBMsg(), null, null);
            return;
        }
        if (msg instanceof OpenChannelMsg) {
            OpenChannelMsg ocm = (OpenChannelMsg) msg;
            // we're a NATted node who needs to open a channel
            tl.openChannel(ocm.getSource(), ocm.getRendezvous(), ocm.getUid());
        }
    }

    public boolean deliverWhenNotReady() {
        return true;
    }

    public Cancellable openChannel(final RendezvousSocketNodeHandle target,
                                   final RendezvousSocketNodeHandle rendezvous,
                                   final RendezvousSocketNodeHandle source,
                                   final int uid,
                                   final Continuation<Integer, Exception> deliverAckToMe,
                                   final Map<String, Object> options) {

        logger.info("openChannel()" + source + "->" + target + " via " + rendezvous + " uid:" + uid + "," + deliverAckToMe + "," + options);

        if (target.getLiveness() > LivenessListener.LIVENESS_DEAD) {
            // if he's dead forever (consider changing this to dead... but think of implications)
            logger.info("openChannel() attempted to open to dead_forever target. Dropping." + source + "->" + target + " via " + rendezvous + " uid:" + uid + "," + deliverAckToMe + "," + options);
            if (deliverAckToMe != null) deliverAckToMe.receiveException(new NodeIsFaultyException(target));
            return null;
        }

        if (target.canContactDirect()) {
            // this is a bug
            throw new IllegalArgumentException("Target must be firewalled.  Target:" + target);
        }

        // we don't want state changing, so this can only be called on the selector
        if (!selectorManager.isSelectorThread()) {
            final AttachableCancellable ret = new AttachableCancellable();
            selectorManager.invoke(new Runnable() {
                public void run() {
                    ret.attach(openChannel(target, rendezvous, source, uid, deliverAckToMe, options));
                }
            });
            return ret;
        }

        OpenChannelMsg msg = new OpenChannelMsg(getAddress(), rendezvous, source, uid);
        logger.debug("routing " + msg + " to " + target);
        final RouteMessage rm =
                new RouteMessage(
                        target.getNodeId(),
                        msg,
                        (byte) thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));
        rm.setDestinationHandle(target);

        // don't reuse the incoming options, it will confuse things
//    rm.setTLOptions(null);

        logger.debug("openChannel(" + target + "," + rendezvous + "," + source + "," + uid + "," + deliverAckToMe + "," + options + ") sending via " + rm);

        // TODO: make PastryNode have a router that does this properly, rather than receiveMessage
        final Cancellable ret = new Cancellable() {

            public boolean cancel() {
                logger.debug("openChannel(" + target + "," + rendezvous + "," + source + "," + uid + "," + deliverAckToMe + "," + options + ").cancel()");
                return rm.cancel();
            }
        };

        // NOTE: Installing this anyway if the LogLevel is high enough is kind of wild, but really useful for debugging
        if ((deliverAckToMe != null) || (logger.isInfoEnabled())) {
            rm.setRouteMessageNotification(new RouteMessageNotification() {
                public void sendSuccess(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, org.urajio.freshpastry.rice.pastry.NodeHandle nextHop) {
                    logger.debug("openChannel(" + target + "," + rendezvous + "," + source + "," + uid + "," + deliverAckToMe + "," + options + ").sendSuccess():" + nextHop);
                    if (deliverAckToMe != null) deliverAckToMe.receiveResult(uid);
                }

                public void sendFailed(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, Exception e) {
                    logger.debug("openChannel(" + target + "," + rendezvous + "," + source + "," + uid + "," + deliverAckToMe + "," + options + ").sendFailed(" + e + ")");
                    if (deliverAckToMe != null) deliverAckToMe.receiveException(e);
                }
            });
        }

        rm.setTLOptions(options);

        thePastryNode.getRouter().route(rm);

        return ret;
    }

    public MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer> sendMessage(
            final RendezvousSocketNodeHandle i,
            final ByteBuffer m,
            final MessageCallback<RendezvousSocketNodeHandle, ByteBuffer> deliverAckToMe,
            Map<String, Object> ops) {
        logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + ops + ")");
        // TODO: use the new method in PastryAppl

        // pull USE_UDP, because that's not going to happen
        final Map<String, Object> options = OptionsFactory.removeOption(ops, WireTransportLayer.OPTION_TRANSPORT_TYPE);

        int priority = 0;
        if (options.containsKey(PriorityTransportLayer.OPTION_PRIORITY)) {
            priority = ((Integer) options.get(PriorityTransportLayer.OPTION_PRIORITY));
        }
        ByteBufferMsg msg = new ByteBufferMsg(m, thePastryNode.getLocalHandle(), priority, getAddress());

        if (options.containsKey(RendezvousTransportLayerImpl.OPTION_USE_PILOT)) {
            RendezvousSocketNodeHandle pilot = (RendezvousSocketNodeHandle) options.get(RendezvousTransportLayerImpl.OPTION_USE_PILOT);
            logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + options + ") sending via " + pilot);
            final MessageRequestHandleImpl<RendezvousSocketNodeHandle, ByteBuffer> ret =
                    new MessageRequestHandleImpl<>(i, m, options);
            ret.setSubCancellable(thePastryNode.send(pilot, new PilotForwardMsg(getAddress(), msg, i), new PMessageNotification() {
                public void sent(PMessageReceipt msg) {
                    if (deliverAckToMe != null) deliverAckToMe.ack(ret);
                }

                public void sendFailed(PMessageReceipt msg, Exception reason) {
                    if (deliverAckToMe != null) {
                        deliverAckToMe.sendFailed(ret, reason);
                    }
                }
            }, null));
            return ret;
        } else {

            final RouteMessage rm =
                    new RouteMessage(
                            i.getNodeId(),
                            msg,
                            (byte) thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));
            rm.setDestinationHandle(i);

            rm.setTLOptions(options);

            logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + options + ") sending via " + rm);

            // TODO: make PastryNode have a router that does this properly, rather than receiveMessage
            final MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer> ret = new MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer>() {

                public boolean cancel() {
                    logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + options + ").cancel()");
                    return rm.cancel();
                }

                public ByteBuffer getMessage() {
                    return m;
                }

                public RendezvousSocketNodeHandle getIdentifier() {
                    return i;
                }

                public Map<String, Object> getOptions() {
                    return options;
                }
            };

            // NOTE: Installing this anyway if the LogLevel is high enough is kind of wild, but really useful for debugging
            if ((deliverAckToMe != null) || (logger.isDebugEnabled())) {
                rm.setRouteMessageNotification(new RouteMessageNotification() {
                    public void sendSuccess(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, org.urajio.freshpastry.rice.pastry.NodeHandle nextHop) {
                        logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + options + ").sendSuccess():" + nextHop);
                        if (deliverAckToMe != null) deliverAckToMe.ack(ret);
                    }

                    public void sendFailed(org.urajio.freshpastry.rice.pastry.routing.RouteMessage message, Exception e) {
                        logger.debug("sendMessage(" + i + "," + m + "," + deliverAckToMe + "," + options + ").sendFailed(" + e + ")");
                        if (deliverAckToMe != null) deliverAckToMe.sendFailed(ret, e);
                    }
                });
            }

            rm.setTLOptions(options);
            thePastryNode.getRouter().route(rm);
            return ret;
        }
    }

    public String toString() {
        return "RendezvousApp{" + thePastryNode + "}";
    }

    public void setTransportLayer(
            RendezvousTransportLayer<RendezvousSocketNodeHandle> tl) {
        this.tl = tl;
    }
}
