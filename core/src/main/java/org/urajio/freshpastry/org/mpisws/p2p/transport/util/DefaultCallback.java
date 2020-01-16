package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayerCallback;

import java.util.Map;

public class DefaultCallback<Identifier, MessageType> implements TransportLayerCallback<Identifier, MessageType> {
    private final static Logger logger = LoggerFactory.getLogger(DefaultCallback.class);

    @Override
    public void incomingSocket(P2PSocket s) {
        logger.info("incomingSocket(" + s + ")");
    }

    public void livenessChanged(Identifier i, int state) {
        logger.info("livenessChanged(" + i + "," + state + ")");
    }

    @Override
    public void messageReceived(Identifier i, MessageType m, Map<String, Object> options) {
        logger.info("messageReceived(" + i + "," + m + ")");
    }
}
