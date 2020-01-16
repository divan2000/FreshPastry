package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.PilotFinder;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;

import java.util.ArrayList;
import java.util.HashSet;

public class LeafSetPilotFinder implements PilotFinder<RendezvousSocketNodeHandle> {
    LeafSet leafSet;
    RandomSource random;

    public LeafSetPilotFinder(PastryNode pn) {
        this.leafSet = pn.getLeafSet();
        this.random = pn.getEnvironment().getRandomSource();
    }

    public RendezvousSocketNodeHandle findPilot(RendezvousSocketNodeHandle dest) {
        if (dest.canContactDirect()) throw new IllegalArgumentException("Dest " + dest + " is not firewalled.");

        if (leafSet.contains(dest)) {
            // generate intermediate node possibilities
            HashSet<RendezvousSocketNodeHandle> possibleIntermediates = new HashSet<>();

            // Note: It's important to make sure the intermediates are in dest's leafset, which means that the distance between the indexes must be <= leafSet.maxSize()/2
            if (leafSet.overlaps()) {
                // small ring, any will do
                for (NodeHandle foo : leafSet) {
                    RendezvousSocketNodeHandle nh = (RendezvousSocketNodeHandle) foo;
                    if (nh.canContactDirect()) possibleIntermediates.add(nh);
                }
            } else {
                int index = leafSet.getIndex(dest);
                int maxDist = leafSet.maxSize() / 2;

                for (int i = -leafSet.ccwSize(); i <= leafSet.cwSize(); i++) {
                    if (i != 0) { // don't select self
                        if (Math.abs(index - i) <= maxDist) {
                            RendezvousSocketNodeHandle nh = (RendezvousSocketNodeHandle) leafSet.get(i);
                            if (nh.canContactDirect()) possibleIntermediates.add(nh);
                        }
                    }
                }
            }

            // return random one
            ArrayList<RendezvousSocketNodeHandle> list = new ArrayList<>(possibleIntermediates);

            // this is scary...
            if (list.isEmpty()) return null;

            return list.get(random.nextInt(list.size()));
        } else {
            // this is out of our scope
            return null;
        }
    }
}
