package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleInputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.commonapi.PastryEndpointMessage;
import org.urajio.freshpastry.rice.pastry.messaging.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * A route message contains a pastry message that has been wrapped to be sent to
 * another pastry node.
 *
 * @author Andrew Ladd
 * @version $Id$
 */
public class RouteMessage extends PRawMessage implements Serializable, org.urajio.freshpastry.rice.p2p.commonapi.RouteMessage {
    public static final short TYPE = -23525;
    private static final long serialVersionUID = 3492981895989180093L;
    public Message internalMsg;
    /**
     * This is used by the Rerouter to keep track of how many attempted reroutes of the message.
     */
    public transient int numRetries = 0;
    boolean hasSender;
    byte internalPriority;
    short internalType;
    // synchronization problem...
    // This is so the RouteMessage can be cancelled
    Cancellable tlCancellable;
    RouteMessageNotification notifyMe;
    private Id target;
    private NodeHandle destinationHandle;
    private transient byte version;
    private NodeHandle prevNode;
    private transient SendOptions opts;
    private int auxAddress;
    private transient NodeHandle nextHop;
    // optimization to not use instanceof in the normal new case
    private transient PRawMessage rawInternalMsg = null;
    private transient InputBuffer serializedMsg;
    private transient PastryNode pn;
    private RMDeserializer endpointDeserializer = new RMDeserializer();
    private transient Map<String, Object> options;

    /**
     * Constructor.
     *
     * @param target           this is id of the node the message will be routed to.
     * @param msg              the wrapped message.
     * @param serializeVersion
     */
    public RouteMessage(Id target, Message msg, byte serializeVersion) {
        this(target, msg, null, null, serializeVersion);
    }

    /**
     * Constructor.
     *
     * @param target           this is id of the node the message will be routed to.
     * @param msg              the wrapped message.
     * @param opts             the send options for the message.
     * @param serializeVersion
     */
    public RouteMessage(Id target, Message msg, SendOptions opts, byte serializeVersion) {
        this(target, msg, null, opts, serializeVersion);
    }

    /**
     * Constructor.
     *
     * @param dest             the node this message will be routed to
     * @param msg              the wrapped message.
     * @param opts             the send options for the message.
     * @param serializeVersion
     */
    public RouteMessage(NodeHandle dest, Message msg, SendOptions opts, byte serializeVersion) {
        this(dest.getNodeId(), msg, dest, opts, serializeVersion);
    }

    /**
     * Constructor.
     *
     * @param target           this is id of the node the message will be routed to.
     * @param msg              the wrapped message.
     * @param firstHop         the nodeHandle of the first hop destination
     * @param serializeVersion
     */
    public RouteMessage(Id target, Message msg, NodeHandle firstHop, byte serializeVersion) {
        this(target, msg, firstHop, null, serializeVersion);
    }


    public RouteMessage(Id target, PRawMessage msg, NodeHandle firstHop, SendOptions opts, byte serializeVersion) {
        this(target, (Message) msg, firstHop, opts, serializeVersion);
        rawInternalMsg = msg;
        if (msg != null) internalType = msg.getType();
    }

    /**
     * Constructor.
     *
     * @param target           this is id of the node the message will be routed to.
     * @param msg              the wrapped message.
     * @param firstHop         the nodeHandle of the first hop destination
     * @param opts             the send options for the message.
     * @param serializeVersion
     */
    public RouteMessage(Id target, Message msg, NodeHandle firstHop, SendOptions opts, byte serializeVersion) {
        super(RouterAddress.getCode());
        this.version = serializeVersion;
        this.target = target;
        internalMsg = msg;
        nextHop = firstHop;
        this.opts = opts;
        if (this.opts == null) {
            this.opts = new SendOptions();
        }
        // can be null on the deserialization, but that ctor properly sets auxAddress
        if (msg != null) {
            auxAddress = msg.getDestination();
        }
    }

    public RouteMessage(Id target, int auxAddress, NodeHandle prev, InputBuffer buf, byte priority, PastryNode pn, NodeHandle destinationHandle, byte serializeVersion) throws IOException {
        this(target, null, null, null, serializeVersion);
        hasSender = buf.readBoolean();
        internalPriority = priority;
        internalType = buf.readShort();
        prevNode = prev;
        serializedMsg = buf;
        this.destinationHandle = destinationHandle;
        this.pn = pn;
        this.auxAddress = auxAddress;
    }

    private static PRawMessage convert(Message msg) {
        if (msg instanceof PRawMessage) {
            PRawMessage prm = (PRawMessage) msg;
            if (prm.getType() == 0)
                if (prm instanceof PJavaSerializedMessage)
                    throw new RuntimeException("Cannot route a PJavaSerializedMessage, this is used internally in RouteMessage." + msg + " " + msg.getClass().getName());
            return prm;
        }
        return new PJavaSerializedMessage(msg);
    }

    /**
     * version 1:
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            int auxAddress                                     +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * + bool hasHndle +        // if it has a destinationHandle instead of an Id
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            Id    target                                       +
     * +  (only existis if the hasHandle boolean is false              +
     * +                                                               +
     * +                                                               +
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            NodeHandle destinationHandle                       +
     * +  (used if the RouteMessage is intended for a specific node)   +
     * +       (only exists if the hasHandle boolean is true)          +
     * ...
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            NodeHandle prev                                    +
     * +  (used to repair routing table during routing)                +
     * +                                                               +
     * ...
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            Internal Message                                   +
     * +  (see below)                                                  +
     * +                                                               +
     * <p>
     * version 0:
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            int auxAddress                                     +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            Id    target                                       +
     * +                                                               +
     * +                                                               +
     * +                                                               +
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            NodeHandle prev                                    +
     * +  (used to repair routing table during routing)                +
     * +                                                               +
     * ...
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +            Internal Message                                   +
     * +  (see below)                                                  +
     * +                                                               +
     *
     * @param buf
     * @return
     */
    public static RouteMessage build(InputBuffer buf, byte priority, PastryNode pn, NodeHandle prev, byte outputVersion) throws IOException {

        byte version = buf.readByte();
        switch (version) {
            case 0: {
                int auxAddress = buf.readInt();
                Id target = Id.build(buf);
                return new RouteMessage(target, auxAddress, prev, buf, priority, pn, null, outputVersion);
            }
            case 1: {
                int auxAddress = buf.readInt();
                NodeHandle destHandle = null;
                Id target = null;
                boolean hasDestHandle = buf.readBoolean();
                if (hasDestHandle) { // destHandle exists
                    destHandle = pn.readNodeHandle(buf);
                    target = (org.urajio.freshpastry.rice.pastry.Id) destHandle.getId();
                } else {
                    target = Id.build(buf);
                }
                return new RouteMessage(target, auxAddress, prev, buf, priority, pn, destHandle, outputVersion);
            }
            default:
                throw new IOException("Unknown Version: " + version);
        }
    }

    /**
     * Gets the target node id of this message.
     *
     * @return the target node id.
     */
    public Id getTarget() {
        return target;
    }

    public NodeHandle getPrevNode() {
        return prevNode;
    }

    public void setPrevNode(NodeHandle n) {
        prevNode = n;
    }

    public NodeHandle getNextHop() {
        return nextHop;
    }

    // Common API Support

    public void setNextHop(NodeHandle nh) {
        nextHop = nh;
    }

    /**
     * Get priority
     *
     * @return the priority of this message.
     */
    @Override
    public int getPriority() {
        if (internalMsg != null)
            return internalMsg.getPriority();
        return internalPriority;
    }

    /**
     * The wrapped message.
     *
     * @return the wrapped message.
     * @deprecated use unwrap(MessageDeserializer)
     */
    public Message unwrap() {
        if (internalMsg != null) {
            return internalMsg;
        }
        try {
            endpointDeserializer.setSubDeserializer(new JavaSerializedDeserializer(pn));
            return unwrap(endpointDeserializer);//pn.getEnvironment().getLogManager().getLogger(RouteMessage.class, null)));
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Get transmission options.
     *
     * @return the options.
     */
    public SendOptions getOptions() {
        if (opts == null) {
            opts = new SendOptions();
        }
        return opts;
    }

    @Override
    public String toString() {
        if (internalMsg == null) {
            return "R[serialized{" + auxAddress + "," + internalType + "}]";
        }

        return "R[" + internalMsg + "]";
    }

    @Override
    public org.urajio.freshpastry.rice.p2p.commonapi.Id getDestinationId() {
        return getTarget();
    }

    @Override
    public void setDestinationId(org.urajio.freshpastry.rice.p2p.commonapi.Id id) {
        target = (Id) id;
    }

    @Override
    public org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle getNextHopHandle() {
        return nextHop;
    }

    @Override
    public void setNextHopHandle(org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle nextHop) {
        this.nextHop = (NodeHandle) nextHop;
    }

    /**
     * @deprecated use getMessage(MessageDeserializer)
     */
    @Override
    public org.urajio.freshpastry.rice.p2p.commonapi.Message getMessage() {
        return ((PastryEndpointMessage) unwrap()).getMessage();
    }

    @Override
    public void setMessage(org.urajio.freshpastry.rice.p2p.commonapi.Message message) {
        ((PastryEndpointMessage) unwrap()).setMessage(message);
    }

    @Override
    public void setMessage(RawMessage message) {
        ((PastryEndpointMessage) unwrap()).setMessage(message);
    }

    @Override
    public org.urajio.freshpastry.rice.p2p.commonapi.Message getMessage(MessageDeserializer md) throws IOException {
        endpointDeserializer.setSubDeserializer(md);
        return ((PastryEndpointMessage) unwrap(endpointDeserializer)).getMessage();
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte(version); // version (deserialized in build())
        buf.writeInt(auxAddress); // (deserialized in build())
        switch (version) {
            case 0:
                target.serialize(buf); // (deserialized in build())
                break;
            case 1:
                buf.writeBoolean(destinationHandle != null); // (deserialized in build())
                if (destinationHandle != null) {
                    destinationHandle.serialize(buf); // (deserialized in build())
                } else {
                    target.serialize(buf); // (deserialized in build())
                }
        } // switch

        if (serializedMsg != null) { // pri, sdr
            // fixed Fabio's bug from Nov 2006 (these were deserialized in the constructer above, but not added back into the internal stream.)
            buf.writeBoolean(hasSender);

            buf.writeShort(internalType);

            // optimize this, possibly by extending InternalBuffer interface to access the raw underlieing bytes
            byte[] raw = new byte[serializedMsg.bytesRemaining()];
            serializedMsg.read(raw);
            buf.write(raw, 0, raw.length);
            serializedMsg = new SimpleInputBuffer(raw);
        } else {
            if (rawInternalMsg == null) {
                rawInternalMsg = convert(internalMsg);
            }
//    address was already peeled off as the auxAddress
//    different wire to deserialize the Address and eliminate unneeded junk
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    +bool hasSender +   Priority    +  Type (Application specifc)   + // zero is java serialization
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//    optional
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    +            NodeHandle sender                                  +
//    +                                                               +
//                      ...  flexable size
//    +                                                               +
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            NodeHandle sender = rawInternalMsg.getSender();
            boolean hasSender = (sender != null);
            if (hasSender) {
                buf.writeBoolean(true);
            } else {
                buf.writeBoolean(false);
            }

            // range check priority
            int priority = rawInternalMsg.getPriority();
            if (priority > Byte.MAX_VALUE)
                throw new IllegalStateException("Priority must be in the range of " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ".  Lower values are higher priority. Priority of " + rawInternalMsg + " was " + priority + ".");
            if (priority < Byte.MIN_VALUE)
                throw new IllegalStateException("Priority must be in the range of " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ".  Lower values are higher priority. Priority of " + rawInternalMsg + " was " + priority + ".");

            short type = rawInternalMsg.getType();
            buf.writeShort(type);

            if (hasSender) {
                sender.serialize(buf);
            }

            rawInternalMsg.serialize(buf);
        }
    }

    public Message unwrap(MessageDeserializer md) throws IOException {
        if (internalMsg != null) {
            return internalMsg;
        }

//  address was already peeled off as the auxAddress
//  different wire to deserialize the Address and eliminate unneeded junk
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  +bool hasSender +   Priority    +  Type (Application specifc)   + // zero is java serialization
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//  optional
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  +            NodeHandle sender                                  +
//  +                                                               +
//                    ...  flexable size
//  +                                                               +
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

        NodeHandle internalSender = null;
        if (hasSender) {
            internalSender = pn.readNodeHandle(serializedMsg);
        }

        internalMsg = (Message) md.deserialize(serializedMsg, internalType, internalPriority, internalSender);

        // the serializedMsg is now dirty, because the unwrapper may change the internal message
        serializedMsg = null;
        pn = null;

        return internalMsg;
    }

    @Override
    public short getType() {
        return TYPE;
    }

    public int getAuxAddress() {
        return auxAddress;
    }

    public short getInternalType() {
        if (rawInternalMsg != null) return rawInternalMsg.getType();
        if (internalMsg != null) {
            if (internalMsg instanceof RawMessage) {
                return ((RawMessage) internalMsg).getType();
            }
            return 0;
        }
        // we don't yet know the internal type because we haven't deserialized it far enough yet
        return -1;
    }

    public NodeHandle getDestinationHandle() {
        return destinationHandle;
    }

    public void setDestinationHandle(NodeHandle handle) {
        destinationHandle = handle;
    }

    public Map<String, Object> getTLOptions() {
        return options;
    }

    public void setTLOptions(Map<String, Object> options) {
        this.options = options;
    }

    public void setTLCancellable(Cancellable c) {
        tlCancellable = c;
    }

    public boolean cancel() {
        return tlCancellable.cancel();
    }

    public void setRouteMessageNotification(RouteMessageNotification notification) {
        notifyMe = notification;
    }

    public void sendSuccess(NodeHandle nextHop) {
        if (notifyMe != null) notifyMe.sendSuccess(this, nextHop);
    }

    /**
     * Return true if it notified a higher layer.
     *
     * @param e
     */
    public boolean sendFailed(Exception e) {
        if (notifyMe != null) notifyMe.sendFailed(this, e);
        return notifyMe != null;
    }

    class RMDeserializer extends PJavaSerializedDeserializer {
        MessageDeserializer sub;

        public RMDeserializer() {
            // the late binding of pn is pretty problematic, we'll set it right before we deserialize
            // the thing is, we usually won't even need it
            super(null);
        }

        public void setSubDeserializer(MessageDeserializer md) {
            sub = md;
        }

        @Override
        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            // just in case we have to do java serialization
            pn = RouteMessage.this.pn;
            return new PastryEndpointMessage(auxAddress, buf, sub, type, priority, sender);
        }
    }
}