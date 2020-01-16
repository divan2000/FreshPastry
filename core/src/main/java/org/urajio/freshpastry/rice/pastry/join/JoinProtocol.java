package org.urajio.freshpastry.rice.pastry.join;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.util.Collection;

public interface JoinProtocol {
    void initiateJoin(Collection<NodeHandle> bootstrap);
}
