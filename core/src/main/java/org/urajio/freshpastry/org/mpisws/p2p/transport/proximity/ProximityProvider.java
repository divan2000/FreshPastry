package org.urajio.freshpastry.org.mpisws.p2p.transport.proximity;

import java.util.Map;

public interface ProximityProvider<Identifier> {
    // the default distance, which is used before a ping
    int DEFAULT_PROXIMITY = 60 * 60 * 1000; // 1 hour

    int proximity(Identifier i, Map<String, Object> options);

    void addProximityListener(ProximityListener<Identifier> listener);

    boolean removeProximityListener(ProximityListener<Identifier> listener);

    void clearState(Identifier i);
}
