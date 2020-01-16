package org.urajio.freshpastry.org.mpisws.p2p.transport.identity;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;

public interface IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> {
    void serialize(OutputBuffer buf, UpperIdentifier i) throws IOException;

    UpperIdentifier deserialize(InputBuffer buf, LowerIdentifier l) throws IOException;

    MiddleIdentifier translateDown(UpperIdentifier i);

    MiddleIdentifier translateUp(LowerIdentifier i);

    void addSerializerListener(SerializerListener<UpperIdentifier> listener);

    void removeSerializerListener(SerializerListener<UpperIdentifier> listener);

}
