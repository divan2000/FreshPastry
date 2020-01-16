package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.util.Iterator;

public interface RouterStrategy {
    NodeHandle pickNextHop(RouteMessage msg, Iterator<NodeHandle> i);
}
