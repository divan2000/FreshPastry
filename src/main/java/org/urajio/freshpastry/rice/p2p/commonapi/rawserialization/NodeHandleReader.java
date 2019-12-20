package org.urajio.freshpastry.rice.p2p.commonapi.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;

import java.io.IOException;

public interface NodeHandleReader {
  /**
   * To use Raw Serialization
   * @param buf
   * @return
   * @throws IOException 
   */
  NodeHandle readNodeHandle(InputBuffer buf) throws IOException;
  NodeHandle coalesce(NodeHandle handle);

}
