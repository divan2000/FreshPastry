package org.urajio.freshpastry.org.mpisws.p2p.transport.nat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.time.TimeSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Drops all incoming TCP connections.
 * Drops all incoming UDP connections that we didn't initiate, or that are since UDP_OPEN_MILLIS
 *
 * @param <Identifier>
 * @param <MessageType>
 * @author Jeff Hoye
 */
public class FirewallTLImpl<Identifier, MessageType> implements TransportLayer<Identifier, MessageType>, TransportLayerCallback<Identifier, MessageType> {
    private final static Logger logger = LoggerFactory.getLogger(FirewallTLImpl.class);

    protected final int UDP_OPEN_MILLIS;

    /**
     * Holds when we last refreshed the UDP connection
     */
    protected Map<Identifier, Long> udpTable;

    protected TransportLayer<Identifier, MessageType> tl;

    protected TransportLayerCallback<Identifier, MessageType> callback;

    protected TimeSource timeSource;

    protected Environment environment;

    /**
     * @param tl
     * @param udp_open_millis how long the udp hole remains open
     */
    public FirewallTLImpl(TransportLayer<Identifier, MessageType> tl, int udp_open_millis, Environment env) {
        this.UDP_OPEN_MILLIS = udp_open_millis;
        this.environment = env;
        this.timeSource = environment.getTimeSource();
        this.udpTable = new HashMap<>();
        this.tl = tl;
        tl.setCallback(this);
        tl.acceptSockets(false);
    }

    @Override
    public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Object> options) {
        long now = timeSource.currentTimeMillis();
        udpTable.put(i, now);
        return tl.sendMessage(i, m, deliverAckToMe, options);
    }

    @Override
    public void messageReceived(Identifier i, MessageType m, Map<String, Object> options) throws IOException {
        if (udpTable.containsKey(i)) {
            long now = timeSource.currentTimeMillis();
            if (udpTable.get(i) + UDP_OPEN_MILLIS >= now) {
                logger.debug("accepting messageReceived(" + i + "," + m + "," + options + ")");
                udpTable.put(i, now);
                callback.messageReceived(i, m, options);
                return;
            }
        }
        logger.debug("dropping messageReceived(" + i + "," + m + "," + options + ")");
    }

    /**
     * Only allow outgoing sockets.
     */
    @Override
    public void incomingSocket(P2PSocket<Identifier> s) {
        logger.debug("closing incomingSocket(" + s + ")");
        s.close();
    }

    @Override
    public void acceptMessages(boolean b) {
        tl.acceptMessages(b);
    }

    @Override
    public void acceptSockets(boolean b) {
    }

    @Override
    public Identifier getLocalIdentifier() {
        return tl.getLocalIdentifier();
    }

    @Override
    public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
        return tl.openSocket(i, deliverSocketToMe, options);
    }

    @Override
    public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
        this.callback = callback;
    }

    @Override
    public void setErrorHandler(ErrorHandler<Identifier> handler) {
    }

    @Override
    public void destroy() {
        tl.destroy();
    }
}
