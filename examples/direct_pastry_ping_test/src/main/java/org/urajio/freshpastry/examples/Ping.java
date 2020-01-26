package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.examples.direct.DirectNodeHandle;
import org.urajio.freshpastry.examples.direct.NetworkSimulator;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.routing.SendOptions;

/**
 * Ping
 * <p>
 * A performance test suite for pastry. This is the per-node app object.
 *
 * @author Rongmei Zhang
 * @version $Id$
 */

public class Ping extends PastryAppl {
  private static int pingAddress = PingAddress.getCode();

  public Ping(PastryNode pn) {
    super(pn);
  }

  public int getAddress() {
    return pingAddress;
  }

  public void sendPing(Id nid) {
    routeMsg(nid, new PingMessageNew(pingAddress, getNodeHandle(), nid),
            new SendOptions());
  }

  public void messageForAppl(Message msg) {

    PingMessageNew pMsg = (PingMessageNew) msg;
    int nHops = pMsg.getHops() - 1;
    double fDistance = pMsg.getDistance();
    double rDistance;

    NetworkSimulator sim = ((DirectNodeHandle) (thePastryNode).getLocalHandle()).getSimulator();
    PingTestRecord tr = (PingTestRecord) (sim.getTestRecord());

    double dDistance = sim.networkDelay(thePastryNode.getLocalHandle(), pMsg.getSender());
    if (dDistance == 0) {
      rDistance = 0;
    } else {
      rDistance = fDistance / dDistance;
    }
    tr.addHops(nHops);
    tr.addDistance(rDistance);
  }

  public boolean enrouteMessage(Message msg, Id from, NodeHandle nextHop,
                                SendOptions opt) {

    PingMessageNew pMsg = (PingMessageNew) msg;
    pMsg.incrHops();
    pMsg.incrDistance(((DirectNodeHandle) (thePastryNode)
            .getLocalHandle()).getSimulator().networkDelay(
            thePastryNode.getLocalHandle(),
            nextHop));

    return true;
  }

  public void leafSetChange(NodeHandle nh, boolean wasAdded) {
  }

  public void routeSetChange(NodeHandle nh, boolean wasAdded) {
  }
}

