package org.urajio.freshpastry.rice.pastry.socket.nat.probe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.AddressStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo.ProbeStrategy;
import org.urajio.freshpastry.org.mpisws.p2p.transport.networkinfo.Prober;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.socket.SocketNodeHandle;
import org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous.RendezvousSocketNodeHandle;
import org.urajio.freshpastry.rice.pastry.transport.PMessageNotification;
import org.urajio.freshpastry.rice.pastry.transport.PMessageReceipt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;

public class ProbeApp extends PastryAppl implements ProbeStrategy {
    private final static Logger logger = LoggerFactory.getLogger(ProbeApp.class);

    Prober prober;
    AddressStrategy addressStrategy;

    public ProbeApp(PastryNode pn, Prober prober, AddressStrategy addressStrategy) {
        super(pn, null, 0, null);
        this.prober = prober;
        this.addressStrategy = addressStrategy;

        setDeserializer(new MessageDeserializer() {
            public org.urajio.freshpastry.rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type,
                                                                                 int priority, org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle sender) throws IOException {
                if (type == ProbeRequestMessage.TYPE) {
                    return ProbeRequestMessage.build(buf, getAddress());
                }
                throw new IllegalArgumentException("Unknown type: " + type);
            }
        });
    }

    @Override
    public void messageForAppl(Message msg) {
        ProbeRequestMessage prm = (ProbeRequestMessage) msg;
        handleProbeRequestMessage(prm);
    }

    public void handleProbeRequestMessage(ProbeRequestMessage prm) {
        logger.debug("handleProbeRequestMessage(" + prm + ")");
        prober.probe(
                addressStrategy.getAddress(((SocketNodeHandle) thePastryNode.getLocalHandle()).getAddress(),
                        prm.getProbeRequester()),
                prm.getUID(), null, null);
    }

    /**
     * Send a ProbeRequestMessage to a node in the leafset.
     * <p>
     * The node must not have the same external address as addr.
     * If no such candidate can be found, use someone who does.
     * If there are no candidates at all, send the message to self (or call handleProbeRequest()
     */
    public Cancellable requestProbe(MultiInetSocketAddress addr, long uid, final Continuation<Boolean, Exception> deliverResultToMe) {
        logger.debug("requestProbe(" + addr + "," + uid + "," + deliverResultToMe + ")");
        // Step 1: find valid helpers

        // make a list of valid candidates
        ArrayList<NodeHandle> valid = new ArrayList<>();

        for (NodeHandle nodeHandle : thePastryNode.getLeafSet()) {
            SocketNodeHandle nh = (SocketNodeHandle) nodeHandle;
            // don't pick self
            if (!nh.equals(thePastryNode.getLocalHandle())) {
                // if nh will send to addr's outermost address
                if (addressStrategy.getAddress(nh.getAddress(), addr).equals(addr.getOutermostAddress())) {
                    valid.add(nh);
                }
            }
        }

        // if there are no valid nodes, use the other nodes
        if (valid.isEmpty()) {
            deliverResultToMe.receiveResult(false);
            return null;
        }

        // Step 2: choose one randomly
        NodeHandle handle = valid.get(thePastryNode.getEnvironment().getRandomSource().nextInt(valid.size()));

        // Step 3: send the probeRequest
        ProbeRequestMessage prm = new ProbeRequestMessage(addr, uid, getAddress());
        return thePastryNode.send(handle, prm, new PMessageNotification() {
            public void sent(PMessageReceipt msg) {
                deliverResultToMe.receiveResult(true);
            }

            public void sendFailed(PMessageReceipt msg, Exception reason) {
                deliverResultToMe.receiveResult(false);
            }
        }, null);
    }

    public Collection<InetSocketAddress> getExternalAddresses() {
        ArrayList<InetSocketAddress> ret = new ArrayList<>();
        for (NodeHandle nh : thePastryNode.getLeafSet()) {
            RendezvousSocketNodeHandle rsnh = (RendezvousSocketNodeHandle) nh;
            if (rsnh.canContactDirect()) {
                ret.add(rsnh.getInetSocketAddress());
            }
        }
        return ret;
    }
}
