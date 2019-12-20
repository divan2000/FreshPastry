package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

public interface RouteMessageNotification {

  void sendFailed(RouteMessage message, Exception e);

  void sendSuccess(RouteMessage message, NodeHandle nextHop);

}
