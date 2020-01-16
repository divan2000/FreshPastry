package org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress;

import java.net.InetSocketAddress;

public class SimpleAddressStrategy implements AddressStrategy {

    /**
     * Method which returns the address of this address
     *
     * @return The address
     */
    public InetSocketAddress getAddress(MultiInetSocketAddress local, MultiInetSocketAddress remote) {
        // start from the outside address, and return the first one not equal to the local address (sans port)

        try {
            for (int ctr = 0; ctr < remote.address.length; ctr++) {
                if (!local.address[ctr].getAddress().equals(remote.address[ctr].getAddress())) {
                    return remote.address[ctr];
                }
            }
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            // same computer
            return remote.getInnermostAddress();
        }
        return remote.address[remote.address.length - 1]; // the last address if we are on the same computer
    }
}
