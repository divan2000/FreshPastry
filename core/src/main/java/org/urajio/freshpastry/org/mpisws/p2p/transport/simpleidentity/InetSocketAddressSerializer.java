package org.urajio.freshpastry.org.mpisws.p2p.transport.simpleidentity;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Serialized version
 * byte IPversion (4 or 6)
 * byte[] address (4 or 16 bytes)
 * short port
 *
 * @author Jeff Hoye
 */
public class InetSocketAddressSerializer implements Serializer<InetSocketAddress>, org.urajio.freshpastry.org.mpisws.p2p.transport.util.Serializer<InetSocketAddress> {
    public static final byte IPV4 = 4;
    public static final byte IPV6 = 6;
    public static final int IPV4_BYTES = 4;
    public static final int IPV6_BYTES = 16;

    public static InetSocketAddress deserializeAddress(InputBuffer b) throws IOException {
        byte version = b.readByte();
        byte[] addr;

        switch (version) {
            case IPV4:
                addr = new byte[IPV4_BYTES];
                break;
            case IPV6:
                addr = new byte[IPV6_BYTES];
                break;
            default:
                throw new IOException("Incorrect IP version, expecting 4 or 6, got " + version);
        }
        b.read(addr);
        short port = b.readShort();
        return new InetSocketAddress(InetAddress.getByAddress(addr), 0xFFFF & port);
    }

    public static void serializeAddress(InetSocketAddress i, OutputBuffer b) throws IOException {
        byte[] addr = i.getAddress().getAddress();
        // write version
        switch (addr.length) {
            case IPV4_BYTES:
                b.writeByte(IPV4);
                break;
            case IPV6_BYTES:
                b.writeByte(IPV6);
                break;
            default:
                throw new IOException("Incorrect number of bytes for IPaddress, expecting 4 or 16, got " + addr.length);
        }
        // write addr
        b.write(addr, 0, addr.length);
        b.writeShort((short) i.getPort());
    }

    public InetSocketAddress deserialize(InputBuffer b, InetSocketAddress i,
                                         Map<String, Object> options) throws IOException {
        return deserializeAddress(b);
    }

    public void serialize(InetSocketAddress i, OutputBuffer b) throws IOException {
        serializeAddress(i, b);
    }

    public int getSerializedLength(InetSocketAddress i) {
        return i.getAddress().getAddress().length + 2 + 1;  // address+port+header
    }

    public InetSocketAddress deserialize(InputBuffer buf) throws IOException {
        return deserializeAddress(buf);
    }

}
