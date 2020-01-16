package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandle;

import java.io.IOException;

/**
 * Maintains RendezvousInfo with the NodeHandle
 *
 * @author Jeff Hoye
 */
public class RendezvousSocketNodeHandle extends SocketNodeHandle implements RendezvousContact {
    /**
     * Internet Routable (or proper port forwarding)
     */
    public static final byte CONTACT_DIRECT = 0;

    /**
     * Not Internet routable
     */
    public static final byte CONTACT_FIREWALLED = 1;

    private byte contactStatus;

    RendezvousSocketNodeHandle(MultiInetSocketAddress eisa, long epoch, Id id, PastryNode node, byte contactStatus) {
        super(eisa, epoch, id, node);
        this.contactStatus = contactStatus;
    }

    static SocketNodeHandle build(InputBuffer buf, PastryNode local) throws IOException {
        MultiInetSocketAddress eaddr = MultiInetSocketAddress.build(buf);
        long epoch = buf.readLong();
        Id nid = Id.build(buf);
        byte contactStatus = buf.readByte();
        return new RendezvousSocketNodeHandle(eaddr, epoch, nid, local, contactStatus);
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        super.serialize(buf);
        buf.writeByte(contactStatus);
    }

    public boolean canContactDirect() {
        return contactStatus != CONTACT_FIREWALLED;
    }

    public byte getContactStatus() {
        return contactStatus;
    }

    public String toString() {
        String s = "[RSNH: " + nodeId + "/" + eaddress;
        if (!canContactDirect()) s += "(FIREWALLED)";
        s += "]";
        return s;
    }
}
