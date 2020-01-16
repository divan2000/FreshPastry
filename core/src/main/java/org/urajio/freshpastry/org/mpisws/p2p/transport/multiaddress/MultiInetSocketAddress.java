package org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress;

import org.urajio.freshpastry.org.mpisws.p2p.transport.simpleidentity.InetSocketAddressSerializer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Class which represets a source route to a remote IP address.
 *
 * @author Jeff Hoye
 * @version $Id
 */
public class MultiInetSocketAddress implements Serializable {
    static InetSocketAddressSerializer serializer = new InetSocketAddressSerializer();

    // the address list, most external first
    protected InetSocketAddress[] address;

    /**
     * Constructor
     *
     * @param address The remote address
     */
    public MultiInetSocketAddress(InetSocketAddress address) {
        this(new InetSocketAddress[]{address});
    }

    public MultiInetSocketAddress(InetSocketAddress[] addressList) {
        this.address = addressList;
    }

    public MultiInetSocketAddress(InetSocketAddress outer,
                                  InetSocketAddress inner) {
        this(new InetSocketAddress[]{outer, inner});
    }

    /**
     * EpochInetSocketAddress: (IPV4):
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   numAddrs    +  IPVersion 0  +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address 0          ...
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port 0                      +  IPVersion 1  +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address 1          ...
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port 1                      +  IPVersion k  +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address k          ...
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port k                      +       ...
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * @param buf
     * @return
     * @throws IOException
     */
    public static MultiInetSocketAddress build(InputBuffer buf) throws IOException {
        byte numAddresses = buf.readByte();
        InetSocketAddress[] saddr = new InetSocketAddress[numAddresses];
        for (int ctr = 0; ctr < numAddresses; ctr++) {
            saddr[ctr] = serializer.deserialize(buf, null, null);
        }
        return new MultiInetSocketAddress(saddr);
    }

    /**
     * Returns the hashCode of this source route
     *
     * @return The hashCode
     */
    public int hashCode() {
        int result = 31173;
        for (InetSocketAddress inetSocketAddress : address) {
            result ^= inetSocketAddress.hashCode();
        }
        return result;
    }

    /**
     * Checks equaltiy on source routes
     *
     * @param o The source route to compare to
     * @return The equality
     */
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof MultiInetSocketAddress)) return false;
        MultiInetSocketAddress that = (MultiInetSocketAddress) o;
        return addressEquals(that);
    }

    public boolean addressEquals(MultiInetSocketAddress that) {
        if (this.address.length != that.address.length) {
            // this code is here so we can say we are the same if one node knows they are firewalled and the other doesn't

//      System.out.println("MulitInetSocketAddress.equals(): "+this+" "+that);
            if (that.getInnermostAddress().equals(this.getInnermostAddress())) {
//        System.out.println("MulitInetSocketAddress.equals(): "+this+" "+that+" "+true);
                return true;
            }
            return false;
        }
        for (int ctr = 0; ctr < this.address.length; ctr++) {
            if (!this.address[ctr].equals(that.address[ctr])) return false;
        }
        return true;
    }

    public String toString() {
        String s = "";
        for (int ctr = 0; ctr < address.length; ctr++) {
            s += address[ctr];
            if (ctr < address.length - 1) s += ":";
        }
        return s;
    }

    public void toStringShort(StringBuffer result) {
        for (int ctr = 0; ctr < address.length; ctr++) {
            InetSocketAddress theAddr = address[ctr];
            InetAddress theAddr2 = theAddr.getAddress();
            if (theAddr2 == null) {
                result.append(theAddr.toString());
            } else {
                String ha = theAddr2.getHostAddress();
                result.append(ha).append(":").append(theAddr.getPort());
            }
            if (ctr < address.length - 1) result.append(";");
        } // ctr
    }

    public InetSocketAddress getInnermostAddress() {
        return address[address.length - 1];
    }

    public InetSocketAddress getOutermostAddress() {
        return address[0];
    }

    public int getNumAddresses() {
        return address.length;
    }

    public InetSocketAddress getAddress(int index) {
        return address[index];
    }

    /**
     * EpochInetSocketAddress: (IPV4):
     * +-+-+-+-+-+-+-+-+
     * +   numAddrs    +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address 0                                          +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port 0                      +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address 1                                          +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port 1                      +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   internet address k                                          +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * +   port k                      +       ...
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * + epoch (long)                                                  +
     * +                                                               +
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * @param buf
     */
    public void serialize(OutputBuffer buf) throws IOException {
//    System.out.println("EISA.serialize():numAddresses:"+address.length);
        buf.writeByte((byte) address.length);
        for (InetSocketAddress inetSocketAddress : address) {
            serializer.serialize(inetSocketAddress, buf);
        }
    }

    public short getSerializedLength() {
        int ret = 1; // num addresses
        for (InetSocketAddress inetSocketAddress : address) {
            ret += serializer.getSerializedLength(inetSocketAddress);
        }
        return (short) ret;
    }

}


