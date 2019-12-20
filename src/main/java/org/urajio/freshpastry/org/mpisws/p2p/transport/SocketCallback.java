package org.urajio.freshpastry.org.mpisws.p2p.transport;


public interface SocketCallback<Identifier> {
  void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock);
  void receiveException(SocketRequestHandle<Identifier> s, Exception ex);
}
