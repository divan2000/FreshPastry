package org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

import java.io.IOException;

public interface RawMessageDeserializer {
    RawMessage deserialize(InputBuffer b, NodeHandle sender) throws IOException;

    void serialize(RawMessage m, OutputBuffer b) throws IOException;
}
