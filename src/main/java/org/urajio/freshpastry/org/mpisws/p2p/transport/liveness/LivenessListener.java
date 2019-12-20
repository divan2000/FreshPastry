package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import java.util.Map;

/**
 * Notified of liveness changes.
 * 
 * @author Jeff Hoye
 *
 */
public interface LivenessListener<Identifier> extends LivenessTypes {

  /**
   * Called when the liveness changes.
   * 
   * @param i
   * @param val
   */
  void livenessChanged(Identifier i, int val, Map<String, Object> options);
}
