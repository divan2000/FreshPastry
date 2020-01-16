package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.OutgoingPilotListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.PilotManager;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.NodeSetEventSource;
import org.urajio.freshpastry.rice.pastry.NodeSetListener;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;

/**
 * Notifies the pilot strategy of leafset changes involving non-natted nodes.
 * <p>
 * Only instantiate this on NATted nodes.
 *
 * @author Jeff Hoye
 */
public class LeafSetPilotStrategy<Identifier extends RendezvousContact> implements NodeSetListener, OutgoingPilotListener<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(LeafSetPilotStrategy.class);

    LeafSet leafSet;
    PilotManager<Identifier> manager;

    public LeafSetPilotStrategy(LeafSet leafSet, PilotManager<Identifier> manager) {
        this.leafSet = leafSet;
        this.manager = manager;
        this.manager.addOutgoingPilotListener(this);

        leafSet.addNodeSetListener(this);
    }

    public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
        logger.debug("nodeSetUpdate(" + handle + ")");
        Identifier nh = (Identifier) handle;
        if (nh.canContactDirect()) {
            if (added) {
                manager.openPilot(nh, null);
            } else {
                manager.closePilot(nh);
            }
        }
    }

    public void pilotOpening(Identifier i) {
    }

    public void pilotClosed(Identifier i) {
        if (leafSet.contains((NodeHandle) i)) {
            manager.openPilot(i, null);
        }
    }
}
