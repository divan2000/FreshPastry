package org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;

/**
 * First, call getMyInetAddress to find the external address.
 * Second, try to use UPnP or user configured portForwarding to configure the NAT
 * Third, call verifyConnectivity to make sure it all worked
 *
 * @author Jeff Hoye
 */
public interface InetSocketAddressLookup extends Destructable {

    /**
     * find nodes outside of our firewall so I can boot
     *
     * @param target
     * @param continuation
     * @param options
     * @return
     */
    Cancellable getExternalNodes(InetSocketAddress bootstrap,
                                 Continuation<Collection<InetSocketAddress>, IOException> c,
                                 Map<String, Object> options);


    /**
     * Returns the local node's InetSocketAddress
     *
     * @param bootstrap who to ask
     * @param c         where the return value is delivered
     * @param options   can be null
     * @return you can cancel the operation
     */
    Cancellable getMyInetAddress(InetSocketAddress bootstrap,
                                 Continuation<InetSocketAddress, IOException> c,
                                 Map<String, Object> options);

    /**
     * Verify that I have connectivity by using a third party.
     * <p>
     * Opens a socket to probeAddress.
     * probeAddress calls ProbeStrategy.requestProbe()
     * probeStrategy forwards the request to another node "Carol"
     * Carol probes local
     *
     * @param bootstrap
     * @param proxyAddr
     * @return
     */
    Cancellable verifyConnectivity(MultiInetSocketAddress local,
                                   InetSocketAddress probeAddresses,
                                   ConnectivityResult deliverResultToMe,
                                   Map<String, Object> options);
}
