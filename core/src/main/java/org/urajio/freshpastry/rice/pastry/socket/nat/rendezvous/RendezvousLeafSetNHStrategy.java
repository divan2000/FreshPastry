package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute.manager.simple.NextHopStrategy;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;

import java.util.Collection;
import java.util.HashSet;

public class RendezvousLeafSetNHStrategy implements NextHopStrategy<MultiInetSocketAddress> {
    LeafSet ls;

    public RendezvousLeafSetNHStrategy(LeafSet leafSet) {
        this.ls = leafSet;
    }

    public Collection<MultiInetSocketAddress> getNextHops(MultiInetSocketAddress destination) {
        if (ls == null) return null;

        return walkLeafSet(destination, 8);
    }

    private Collection<MultiInetSocketAddress> walkLeafSet(MultiInetSocketAddress destination, int numRequested) {
        Collection<MultiInetSocketAddress> result = new HashSet<>();
        LeafSet leafset = ls;
        for (int i = 1; i < leafset.maxSize() / 2; i++) {
            RendezvousSocketNodeHandle snh = (RendezvousSocketNodeHandle) leafset.get(-i);
            if (snh != null && !snh.eaddress.equals(destination) && snh.canContactDirect()) {
                result.add(snh.eaddress);
                if (result.size() >= numRequested) return result;
            }
            snh = (RendezvousSocketNodeHandle) leafset.get(i);
            if (snh != null && !snh.eaddress.equals(destination) && snh.canContactDirect()) {
                result.add(snh.eaddress);
                if (result.size() >= numRequested) return result;
            }
        }
        return result;
    }
}
