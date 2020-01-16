package org.urajio.freshpastry.rice.pastry.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi.RawMessageDeserializer;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Message;
import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.NodeHandleFactory;
import org.urajio.freshpastry.rice.pastry.messaging.PRawMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TLDeserializer implements RawMessageDeserializer, Deserializer {
    private final static Logger logger = LoggerFactory.getLogger(TLDeserializer.class);
    protected Environment environment;
    Map<Integer, MessageDeserializer> deserializers;
    NodeHandleFactory nodeHandleFactory;

    public TLDeserializer(NodeHandleFactory nodeHandleFactory, Environment env) {
        this.environment = env;
        this.nodeHandleFactory = nodeHandleFactory;
        this.deserializers = new HashMap<>();
    }

    public RawMessage deserialize(InputBuffer buf, NodeHandle sender) throws IOException {

        int address = buf.readInt();
        byte priority = buf.readByte();
        short type = buf.readShort();

        // TODO: Think about how to make this work right.  Maybe change the default deserializer?
        MessageDeserializer deserializer = getDeserializer(address);
        if (deserializer == null) {
            throw new IOException("Unknown address:" + address);  // TODO: Make UnknownAddressException
        }

        Message msg = deserializer.deserialize(buf, type, priority, sender);
        if (msg == null) {
            logger.warn(String.format("Deserialized message to null! d:%s a:%d t:%s p:%s s:%s b:%s", deserializer, address, type, priority, sender, buf));
        }
        logger.debug("deserialize(): " + msg);
        return (RawMessage) msg;
    }

    public void serialize(RawMessage m, OutputBuffer o) throws IOException {
        PRawMessage msg = (PRawMessage) m;
        int address = msg.getDestination();
        o.writeInt(address);

        // range check priority
        int priority = msg.getPriority();
        if (priority > Byte.MAX_VALUE)
            throw new IllegalStateException("Priority must be in the range of " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ".  Lower values are higher priority. Priority of " + msg + " was " + priority + ".");
        if (priority < Byte.MIN_VALUE)
            throw new IllegalStateException("Priority must be in the range of " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ".  Lower values are higher priority. Priority of " + msg + " was " + priority + ".");
        o.writeByte((byte) priority);

        short type = msg.getType();
        o.writeShort(type);
        msg.serialize(o);
    }

    public void clearDeserializer(int address) {
        deserializers.remove(address);
    }

    public MessageDeserializer getDeserializer(int address) {
        return deserializers.get(address);
    }

    public void setDeserializer(int address, MessageDeserializer md) {
        deserializers.put(address, md);
    }
}
