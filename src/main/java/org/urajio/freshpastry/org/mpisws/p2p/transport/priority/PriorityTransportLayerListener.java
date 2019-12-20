package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayerListener;

import java.util.Map;

public interface PriorityTransportLayerListener<Identifier> extends TransportLayerListener<Identifier> {
  void enqueued(int bytes, Identifier i, Map<String, Object> options, boolean passthrough, boolean socket);
  void dropped(int bytes, Identifier i, Map<String, Object> options, boolean passthrough, boolean socket);
}
