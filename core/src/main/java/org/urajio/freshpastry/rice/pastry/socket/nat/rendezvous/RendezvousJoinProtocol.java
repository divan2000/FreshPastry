package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.ContactDirectStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.PilotManager;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayerImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.OptionsFactory;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.ReadyStrategy;
import org.urajio.freshpastry.rice.pastry.join.JoinRequest;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.routing.RoutingTable;
import org.urajio.freshpastry.rice.pastry.standard.ConsistentJoinProtocol;

import java.io.IOException;
import java.util.Map;

/**
 * The purpose of this class is to allow a NATted node to boot.
 * <p>
 * Without this class, when the JoinRequest reaches the nearest neighbor of the joiner,
 * that node can't deliver the Request back to the joiner (because he is NATted).
 * <p>
 * The class opens a pilot to the bootstrap, then includes this node in the RendezvousJoinRequest.
 * <p>
 * Note that this class uses both JoinRequests and RendezvousJoinRequests.  The latter are only used for
 * a NATted Joiner.
 * <p>
 * Overview:
 * Extend CJPSerializer to also use RendezvousJoinRequest (include the bootstrap, and possibly additional credentials)
 * pass in constructor
 * <p>
 * Override handleInitiateJoin():
 * If local node is NATted:
 * open a pilot to the bootstrap (make sure to complete this before continuing)
 * send the RendezvousJoinRequest
 * else
 * super.handleInitiateJoin()
 * <p>
 * TODO:
 * Override respondToJoiner():
 * If joiner is NATted:
 * use the pilot on the bootstrap:
 * rendezvousLayer.requestSocket(joiner, bootstrap)
 * <p>
 * Override completeJoin() to close the pilot to the bootstrap before calling super.completeJoin() because that will cause pilots to open.
 * may need a way to verify that it is closed, or else don't close it if it's in the leafset, b/c it may become busted
 *
 * @author Jeff Hoye
 */
public class RendezvousJoinProtocol extends ConsistentJoinProtocol {
    private final static Logger logger = LoggerFactory.getLogger(RendezvousJoinProtocol.class);

    PilotManager<RendezvousSocketNodeHandle> pilotManager;

    public RendezvousJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt,
                                  LeafSet ls, ReadyStrategy nextReadyStrategy, PilotManager<RendezvousSocketNodeHandle> pilotManager) {
        super(ln, lh, rt, ls, nextReadyStrategy, new RCJPDeserializer(ln));
        this.pilotManager = pilotManager;
    }

    /**
     * Use RendezvousJoinRequest if local node is NATted
     */
    @Override
    protected void getJoinRequest(NodeHandle b, final Continuation<JoinRequest, Exception> deliverJRToMe) {
        final RendezvousSocketNodeHandle bootstrap = (RendezvousSocketNodeHandle) b;


        // if I can be contacted directly by anyone, or I can contact the bootstrap despite the fact that he's firewalled, then call super
        ContactDirectStrategy<RendezvousSocketNodeHandle> contactStrat =
                (ContactDirectStrategy<RendezvousSocketNodeHandle>)
                        thePastryNode.getVars().get(RendezvousSocketPastryNodeFactory.RENDEZVOUS_CONTACT_DIRECT_STRATEGY);
        if ((((RendezvousSocketNodeHandle) thePastryNode.getLocalHandle()).canContactDirect()) || // I can be contacted directly
                (!bootstrap.canContactDirect() && contactStrat.canContactDirect(bootstrap))) { // I can contact the bootstrap even though he's firewalled
            super.getJoinRequest(bootstrap, deliverJRToMe);
            return;
        }

        // TODO: Throw exception if can't directly contact the bootstrap

        // open the pilot before sending the JoinRequest.
        logger.debug("opening pilot to " + bootstrap);
        pilotManager.openPilot(bootstrap,
                new Continuation<SocketRequestHandle<RendezvousSocketNodeHandle>, Exception>() {

                    public void receiveException(Exception exception) {
                        deliverJRToMe.receiveException(exception);
                    }

                    public void receiveResult(
                            SocketRequestHandle<RendezvousSocketNodeHandle> result) {
                        RendezvousJoinRequest jr = new RendezvousJoinRequest(localHandle, thePastryNode
                                .getRoutingTable().baseBitLength(), thePastryNode.getEnvironment().getTimeSource().currentTimeMillis(), bootstrap);
                        deliverJRToMe.receiveResult(jr);
                    }
                });
    }

    /**
     * This is called from respondToJoiner() and other places, we need to set the OPTION_USE_PILOT
     * to the intermediate node, so that will queue the RendezvousTL to use the pilot.
     */
    @Override
    protected Map<String, Object> getOptions(JoinRequest jr, Map<String, Object> existing) {
        if (jr.accepted()) {
            if (jr instanceof RendezvousJoinRequest) {
                RendezvousJoinRequest rjr = (RendezvousJoinRequest) jr;
                return OptionsFactory.addOption(existing, RendezvousTransportLayerImpl.OPTION_USE_PILOT, rjr.getPilot());
            }
        }
        return existing;
    }

    static class RCJPDeserializer extends CJPDeserializer {
        public RCJPDeserializer(PastryNode pn) {
            super(pn);
        }

        @Override
        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            if (type == RendezvousJoinRequest.TYPE) {
                return new RendezvousJoinRequest(buf, pn, sender, pn);
            }
            return super.deserialize(buf, type, priority, sender);
        }
    }
}
