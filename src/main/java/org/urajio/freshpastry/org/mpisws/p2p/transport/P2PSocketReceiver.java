package org.urajio.freshpastry.org.mpisws.p2p.transport;

import java.io.IOException;

public interface P2PSocketReceiver<Identifier> {
  /**
   * Called when a socket is available for read/write
   */
  void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException;
  /**
   * Called when there is an error
   */
  void receiveException(P2PSocket<Identifier> socket, Exception ioe); 
}
