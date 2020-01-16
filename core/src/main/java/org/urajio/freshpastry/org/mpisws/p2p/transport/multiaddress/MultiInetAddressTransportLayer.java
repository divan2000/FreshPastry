package org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;

import java.nio.ByteBuffer;

/**
 * This transport layer that can allow a node behind a NAT to talk to nodes outside or inside the firewall.
 * <p>
 * Could concievably be used to allow a node with multiple NICs pick the best address for different nodes.
 * Perhaps this would be useful if you were running a node on the NAT.
 *
 * @author Jeff Hoye
 */
public interface MultiInetAddressTransportLayer extends TransportLayer<MultiInetSocketAddress, ByteBuffer> {
    AddressStrategy getAddressStrategy();
}
