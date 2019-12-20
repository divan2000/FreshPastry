package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import java.util.Map;

/**
 * Notified about primary socket connections.
 * 
 * @author Jeff Hoye
 *
 */
public interface PrimarySocketListener<Identifier> {

  void notifyPrimarySocketOpened(Identifier i, Map<String, Object> options);
  
  void notifyPrimarySocketClosed(Identifier i);
}
