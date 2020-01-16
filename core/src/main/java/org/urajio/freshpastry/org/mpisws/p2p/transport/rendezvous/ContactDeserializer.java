package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public interface ContactDeserializer<Identifier, HighIdentifier> {

    void serialize(HighIdentifier i, OutputBuffer buf) throws IOException;

    ByteBuffer serialize(HighIdentifier i) throws IOException;

    HighIdentifier deserialize(InputBuffer sib) throws IOException;

    Identifier convert(HighIdentifier high);

    /**
     * Return the options that all the layers would make on this identifier.
     *
     * @param high
     * @return
     */
    Map<String, Object> getOptions(HighIdentifier high);
}
