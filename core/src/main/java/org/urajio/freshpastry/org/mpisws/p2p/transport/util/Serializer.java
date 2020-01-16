package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;

public interface Serializer<Identifier> {
    void serialize(Identifier i, OutputBuffer buf) throws IOException;
//  public ByteBuffer serialize(Identifier i);

    Identifier deserialize(InputBuffer buf) throws IOException;
}
