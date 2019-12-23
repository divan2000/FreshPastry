package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;

public interface Deserializer {
    void setDeserializer(int address, MessageDeserializer md);

    void clearDeserializer(int address);

    MessageDeserializer getDeserializer(int address);
}
