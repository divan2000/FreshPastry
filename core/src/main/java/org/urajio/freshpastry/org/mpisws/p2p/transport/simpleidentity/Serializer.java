package org.urajio.freshpastry.org.mpisws.p2p.transport.simpleidentity;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.util.Map;

public interface Serializer<Identifier> {
    void serialize(Identifier i, OutputBuffer b) throws IOException;

    Identifier deserialize(InputBuffer b, Identifier i, Map<String, Object> options) throws IOException;
}
