package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import java.nio.ByteBuffer;
import java.util.Map;

public interface MessageInfo {
  ByteBuffer getMessage();
  Map<String, Object> getOptions();
  int getPriroity();
  int getSize();
}
