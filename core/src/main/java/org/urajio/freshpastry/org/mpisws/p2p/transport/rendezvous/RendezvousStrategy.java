package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageCallback;
import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Uses a 3rd party channel to request a node to connect to a dest.
 *
 * @author Jeff Hoye
 */
public interface RendezvousStrategy<Identifier> {
    int SUCCESS = 1;

    /**
     * Calls ChannelOpener.openChannel(dest, credentials) on target
     * <p>
     * Possible exceptions to deliverResultToMe:
     * NodeIsFaultyException if target is faulty
     * UnableToConnectException if dest is faulty
     * <p>
     * Called by:
     * 1) Rendezvous if the target and source are NATted
     * 2) Source if target is NATted, but source isn't
     * <p>
     * <p>
     * Not called if the pilotFinder found a pilot for the target (in FreePastry this means that this will not be called
     * if the target is in the leafSet).
     *
     * @param target            call ChannelOpener.openChannel() on this Identifier
     * @param rendezvous        pass this to ChannelOpener.openChannel(), it's who the ChannelOpener will connect to
     * @param credentials       this is also passed to ChannelOpener.openChannel()
     * @param deliverResultToMe notify me when success/failure
     * @return a way to cancel the request
     */
    Cancellable openChannel(Identifier target, Identifier rendezvous, Identifier source, int uid, Continuation<Integer, Exception> deliverResultToMe, Map<String, Object> options);

    /**
     * Sends the message via an out-of-band channel.  Usually routing.
     *
     * @param i
     * @param m
     * @param deliverAckToMe
     * @param options
     * @return
     */
    MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i, ByteBuffer m, MessageCallback<Identifier, ByteBuffer> deliverAckToMe, Map<String, Object> options);

    void setTransportLayer(RendezvousTransportLayer<Identifier> ret);
}
