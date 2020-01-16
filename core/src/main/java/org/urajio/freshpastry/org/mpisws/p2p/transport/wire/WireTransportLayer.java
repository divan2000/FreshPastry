package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.urajio.freshpastry.org.mpisws.p2p.transport.TransportLayer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Sends/receives ByteBuffer from InetSocketAddress
 * <p>
 * This layer does a lot of the difficult part:
 * <p>
 * - Non-blocking I/O (using selector etc)
 * - Enforcement of number of Sockets to prevent FileDescriptor Starvation
 *
 * @author Jeff Hoye
 */
public interface WireTransportLayer extends TransportLayer<InetSocketAddress, ByteBuffer> {

    String OPTION_TRANSPORT_TYPE = "transport_type";
    int TRANSPORT_TYPE_DATAGRAM = 0;
    /**
     * Note this does not provide end-to-end guarantee.  Only per-hop guarantee.
     */
    int TRANSPORT_TYPE_GUARANTEED = 1;

}
