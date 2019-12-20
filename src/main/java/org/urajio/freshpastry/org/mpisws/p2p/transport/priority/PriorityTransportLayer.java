package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;
import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayerListener;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Does 3 things:
 *  a) Sends messages on a Socket (depending on the options).  
 *  b) Prioritizes messages into queues.
 *  c) calls sendFailed if there is a liveness change
 *  
 * @author Jeff Hoye
 */
public interface PriorityTransportLayer<Identifier> extends TransportLayer<Identifier, ByteBuffer> {
  String OPTION_PRIORITY = "OPTION_PRIORITY";
  
  // different priority levels
  byte MAX_PRIORITY = -15;
  byte HIGH_PRIORITY = -10;
  byte MEDIUM_HIGH_PRIORITY = -5;
  byte MEDIUM_PRIORITY = 0;
  byte MEDIUM_LOW_PRIORITY = 5;
  byte LOW_PRIORITY = 10;
  byte LOWEST_PRIORITY = 15;
  byte DEFAULT_PRIORITY = MEDIUM_PRIORITY;

  int STATUS_NOT_CONNECTED = 0;
  int STATUS_CONNECTING = 1;
  int STATUS_CONNECTED = 2;
  
  
  void addTransportLayerListener(TransportLayerListener<Identifier> listener);
  void removeTransportLayerListener(TransportLayerListener<Identifier> listener);
  void addPriorityTransportLayerListener(PriorityTransportLayerListener<Identifier> listener);
  void removePriorityTransportLayerListener(PriorityTransportLayerListener<Identifier> listener);

  /**
   * Returns if there is a primary connection to the identifier
   * 
   * @param i
   * @return STATUS_NOT_CONNECTED, STATUS_CONNECTING, STATUS_CONNECTED
   */
  int connectionStatus(Identifier i);
  
  /**
   * Returns the options on the primary connection
   * @param i
   * @return
   */
  Map<String, Object> connectionOptions(Identifier i);
  
  /**
   * usually used with bytesPending() or queueLength()
   * @return any Identifier with messages to be sent
   */
  Collection<Identifier> nodesWithPendingMessages();
  
  /**
   * Returns the number of messages pending to be sent
   * @param i
   * @return
   */
  int queueLength(Identifier i);
  
  /**
   * The number of bytes to be sent to the identifier
   * @param i
   * @return
   */
  long bytesPending(Identifier i);
  
  /**
   * The number of bytes to be sent to the identifier
   * @param i
   * @return
   */
  List<MessageInfo> getPendingMessages(Identifier i);
  
  /**
   * open a primary connection
   * @param i
   * @param notifyMe when it is open
   */
  void openPrimaryConnection(Identifier i, Map<String, Object> options);
  
  void addPrimarySocketListener(PrimarySocketListener<Identifier> listener);
  void removePrimarySocketListener(PrimarySocketListener<Identifier> listener);

}
