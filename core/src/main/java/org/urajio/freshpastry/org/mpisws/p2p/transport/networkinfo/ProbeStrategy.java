package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * This is used in a network
 *
 * @author Jeff Hoye
 */
public interface ProbeStrategy {
    /**
     * Finds another node in the network and asks them to probe the addr with the uid
     * <p>
     * calls Prober.probe() on another node
     *
     * @param addr the location of the requestor (who we need to probe)
     * @param uid  a unique identifier created by the original requestor at addr
     * @return can cancel the operation
     */
    Cancellable requestProbe(MultiInetSocketAddress addr, long uid, Continuation<Boolean, Exception> deliverResultToMe);

    /**
     * Returns some known external addresses.
     *
     * @return
     */
    Collection<InetSocketAddress> getExternalAddresses();
}
