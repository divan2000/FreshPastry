package org.urajio.freshpastry.rice.pastry;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

/**
 * Internal interface to get a NodeHandle from some other identifier, such as an InetSocketAddress.
 * Get's the NodeHandle from the network (not already cached).
 *
 * @author Jeff Hoye
 */
public interface NodeHandleFetcher {
    Cancellable getNodeHandle(Object o, Continuation<NodeHandle, Exception> c);
}
