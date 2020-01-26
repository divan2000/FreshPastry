package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.rice.p2p.commonapi.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

/**
 * @author Jeff Hoye
 */
public class HelloMsg extends Message {
    private Id target;
    private boolean messageDirect = false;
    private int msgid;
    private NodeHandle src;

    public HelloMsg(int addr, NodeHandle src, Id tgt, int mid) {
        super(addr);
        target = tgt;
        msgid = mid;
        this.src = src;
    }

    public String toString() {
        return "Hello #" + msgid + " from " + src.getId();
    }

    public String getInfo() {
        String s = toString();
        if (messageDirect) {
            s += " direct";
        } else {
            s += " routed";
        }
        return s;
    }

    public int getId() {
        return msgid;
    }
}
