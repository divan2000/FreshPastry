package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Called by the ProbeStrategy
 *
 * @author Jeff Hoye
 */
public interface Prober {
    /**
     * @param addr                the address to probe
     * @param uid                 the uid with the probe
     * @param deliverResponseToMe let me know how it goes
     * @param options             tl options
     * @return call me to cancel
     */
    Cancellable probe(InetSocketAddress addr,
                      long uid,
                      Continuation<Long, Exception> deliverResponseToMe,
                      Map<String, Object> options);

}
