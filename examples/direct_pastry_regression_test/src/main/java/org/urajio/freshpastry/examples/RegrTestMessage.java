package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

class RegrTestMessage extends Message {
    public NodeHandle sourceNode;

    //public Id source;
    public Id target;

    public RegrTestMessage(int addr, NodeHandle src, Id tgt) {
        super(addr);
        sourceNode = src;
        target = tgt;
    }

    public String toString() {
        String s = "";
        s += "RTMsg from " + sourceNode + " to " + target;
        return s;
    }
}
