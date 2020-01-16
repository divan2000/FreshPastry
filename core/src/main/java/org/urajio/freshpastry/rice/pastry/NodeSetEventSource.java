package org.urajio.freshpastry.rice.pastry;

public interface NodeSetEventSource {
    void addNodeSetListener(NodeSetListener listener);

    void removeNodeSetListener(NodeSetListener listener);
}
