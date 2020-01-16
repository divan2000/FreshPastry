package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.io.IOException;

public class NodeIsFirewalledException extends IOException {
    NodeHandle handle;

    public NodeIsFirewalledException(NodeHandle handle) {
        super("Node " + handle + " is firewalled.");
        this.handle = handle;
    }

    public NodeHandle getHandle() {
        return handle;
    }
}
