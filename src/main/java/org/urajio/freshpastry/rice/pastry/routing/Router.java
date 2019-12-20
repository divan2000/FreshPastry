package org.urajio.freshpastry.rice.pastry.routing;

import java.util.Iterator;

import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;

/**
 * Router is no longer just an application.  It is privileged.
 * 
 * @author Jeff Hoye
 *
 */
public interface Router {

  /**
   * Send the RouteMessage based on the Pastry Algorithm
   * @param rm
   */
  void route(RouteMessage rm);

  /**
   * Returns an ordered list of the best candidates for the next to the key.  Always starts with
   * a node that matches an additional prefix, if it is available.
   * 
   * @param key
   * @return
   */
  Iterator<NodeHandle> getBestRoutingCandidates(Id key);

//  boolean routeMessage(RouteMessage rm);

}
