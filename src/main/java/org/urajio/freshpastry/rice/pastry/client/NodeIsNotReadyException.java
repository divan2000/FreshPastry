package org.urajio.freshpastry.rice.pastry.client;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

public class NodeIsNotReadyException extends Exception {
  NodeHandle handle;
  
  public NodeIsNotReadyException(NodeHandle handle) {
    this.handle = handle;
  }

}
