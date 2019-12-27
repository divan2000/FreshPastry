package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.util.Map;

public interface PMessageReceipt extends MessageRequestHandle<NodeHandle, Message> {
    NodeHandle getIdentifier();

    Message getMessage();

    Map<String, Object> getOptions();
}
