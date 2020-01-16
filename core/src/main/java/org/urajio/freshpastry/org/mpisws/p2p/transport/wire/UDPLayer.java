package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.rice.Destructable;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

public interface UDPLayer extends Destructable {

    void acceptMessages(boolean b);

    MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
            InetSocketAddress destination, ByteBuffer m,
            MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe,
            Map<String, Object> options);
}
