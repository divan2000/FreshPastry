package org.urajio.freshpastry.rice.p2p.commonapi.rawserialization;

import java.io.IOException;

public interface RawSerializable {
  void serialize(OutputBuffer buf) throws IOException;
}
