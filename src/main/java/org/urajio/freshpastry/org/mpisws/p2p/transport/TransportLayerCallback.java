package org.urajio.freshpastry.org.mpisws.p2p.transport;

import java.io.IOException;
import java.util.Map;

/**
 * Used to receive incoming messages/sockets.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <MessageType>
 */
public interface TransportLayerCallback<Identifier, MessageType> {
  /**
   * Called when a new message is received.
   * 
   * @param i The node it is coming from
   * @param m the message
   * @param options describe how the message arrived (udp/tcp, encrypted etc)
   * @throws IOException if there is a problem decoding the message
   */
  void messageReceived(Identifier i, MessageType m, Map<String, Object> options) throws IOException;
  /**
   * Notification of a new socket.
   * 
   * @param s the incoming socket
   * @throws IOException
   */
  void incomingSocket(P2PSocket<Identifier> s) throws IOException;
}
