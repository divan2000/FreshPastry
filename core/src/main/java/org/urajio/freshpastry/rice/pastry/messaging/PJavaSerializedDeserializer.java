package org.urajio.freshpastry.rice.pastry.messaging;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.io.IOException;

/**
 * The purpose of this class is just for programming convienience to disambiguate
 * between rice.p2p.commonapi and rice.pastry with the interfaces/classes
 * Message
 * NodeHandle
 *
 * @author Jeff Hoye
 */
public abstract class PJavaSerializedDeserializer extends JavaSerializedDeserializer {

    public PJavaSerializedDeserializer(PastryNode pn) {
        super(pn);
    }

    public abstract Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException;

    public org.urajio.freshpastry.rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority, org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle sender) throws IOException {
        org.urajio.freshpastry.rice.p2p.commonapi.Message ret = deserialize(buf, type, priority, (NodeHandle) sender);
        if (ret == null) return super.deserialize(buf, type, priority, sender);
        return ret;
    }

}
