package org.urajio.freshpastry.rice.pastry;

public interface NodeSetListener {
    void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added);
}
