package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.AddressStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.ContactDirectStrategy;
import org.urajio.freshpastry.rice.environment.Environment;

public class RendezvousContactDirectStrategy implements ContactDirectStrategy<RendezvousSocketNodeHandle> {
    private final static Logger logger = LoggerFactory.getLogger(RendezvousContactDirectStrategy.class);

    MultiInetSocketAddress localAddr;
    Environment environment;
    AddressStrategy addressStrategy;

    public RendezvousContactDirectStrategy(RendezvousSocketNodeHandle localNodeHandle, AddressStrategy addressStrategy, Environment environment) {
        this.localAddr = localNodeHandle.getAddress();
        this.environment = environment;
        this.addressStrategy = addressStrategy;
    }

    /**
     * Return true if they're behind the same firewall
     * <p>
     * If the address I should use to contact the node is the same as his internal address
     */
    public boolean canContactDirect(RendezvousSocketNodeHandle remoteNode) {
        if (remoteNode.canContactDirect()) return true;

        MultiInetSocketAddress a = remoteNode.getAddress();
        if (a.getNumAddresses() == 1) {
            // they're on the same physical node
            return a.getInnermostAddress().getAddress().equals(localAddr.getInnermostAddress().getAddress());
        } else {
            boolean ret = addressStrategy.getAddress(localAddr, a).equals(a.getInnermostAddress());
            logger.debug("rendezvous contacting direct:" + remoteNode);
            return ret;
        }
    }
}
