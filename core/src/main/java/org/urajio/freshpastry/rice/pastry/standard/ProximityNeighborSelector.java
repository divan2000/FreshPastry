package org.urajio.freshpastry.rice.pastry.standard;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.util.Collection;

/**
 * Finds a near neighbor (usually for bootstrapping)
 *
 * @author Jeff Hoye
 */
public interface ProximityNeighborSelector {
    Cancellable getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe);
}
