package org.urajio.freshpastry.rice.p2p.commonapi.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.Message;

public interface RawMessage extends Message, RawSerializable {
  short getType();
}
