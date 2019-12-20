package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import java.util.Map;

/**
 * Expands the Transport Layer to include pings and liveness checks.
 * 
 * @author Jeff Hoye
 *
 */
public interface LivenessProvider<Identifier> extends LivenessTypes {
  
  int getLiveness(Identifier i, Map<String, Object> options);
  
  /**
   * Returns whether a new notification will occur.
   * 
   * Will return false if a liveness check has recently completed.
   * 
   * Will return true if a new liveness check starts, or an existing one is in progress.
   * 
   * @param i the node to check
   * @return true if there will be an update (either a ping, or a change in liveness)
   * false if there won't be an update due to bandwidth concerns
   */
  boolean checkLiveness(Identifier i, Map<String, Object> options);
  
  void addLivenessListener(LivenessListener<Identifier> name);
  boolean removeLivenessListener(LivenessListener<Identifier> name);
  
  /**
   * Force layer to clear the existing state related to the Identifier.  Usually 
   * if there is reason to believe a node has returned.
   * 
   * @param i
   */
  void clearState(Identifier i);
}
