package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

public class BogusUDPLayerImpl implements UDPLayer {

    public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
            InetSocketAddress destination, ByteBuffer m,
            MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe,
            Map<String, Object> options) {
        MessageRequestHandle<InetSocketAddress, ByteBuffer> ret =
                new MessageRequestHandleImpl<>(destination, m, options);
        if (deliverAckToMe != null) {
            deliverAckToMe.sendFailed(ret, new IOException("UDP is disabled.  To use this feature, enable UDP in the WireTransportLayer constructor."));
        }
        return ret;
    }

    public void destroy() {
        // TODO Auto-generated method stub
    }

    public void acceptMessages(boolean b) {
        // TODO Auto-generated method stub
    }
}
