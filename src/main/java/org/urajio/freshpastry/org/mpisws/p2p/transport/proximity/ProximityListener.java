package org.urajio.freshpastry.org.mpisws.p2p.transport.proximity;

import java.util.Map;

public interface ProximityListener<Identifier> {
  void proximityChanged(Identifier i, int newProximity, Map<String, Object> options);
}
