package org.urajio.freshpastry.examples.transport.direct;

import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;

import java.io.IOException;

public class ConnectorExceptionDelivery<Identifier> implements Delivery {
    IOException e;
    SocketCallback<Identifier> connectorReceiver;
    SocketRequestHandle<Identifier> connectorHandle;

    public ConnectorExceptionDelivery(SocketCallback<Identifier> connectorReceiver, SocketRequestHandle<Identifier> connectorHandle, IOException e) {
        this.e = e;
        this.connectorReceiver = connectorReceiver;
        this.connectorHandle = connectorHandle;
    }

    public void deliver() {
        connectorReceiver.receiveException(connectorHandle, e);
    }

    // out of band, needs to get in front of any other message
    public int getSeq() {
        return -1;
    }
}
