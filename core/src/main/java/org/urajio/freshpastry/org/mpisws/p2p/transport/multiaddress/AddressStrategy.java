package org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress;

import java.net.InetSocketAddress;

/**
 * Return the InetSocketAddress to use for this EpochInetSocketAddress
 *
 * @author Jeff Hoye
 */
public interface AddressStrategy {
    InetSocketAddress getAddress(MultiInetSocketAddress local, MultiInetSocketAddress remote);
}
