package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.CommonAPIAppl;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;
import org.urajio.freshpastry.rice.pastry.routing.SendOptions;

import java.io.IOException;

/**
 * RegrTestApp
 * <p>
 * A regression test suite for pastry. This is the per-node app object.
 *
 * @author andrew ladd
 * @author peter druschel
 * @version $Id$
 */

public class RegrTestApp extends CommonAPIAppl {

    private static int addr = RTAddress.getCode();
    private PastryRegrTest prg;

    public RegrTestApp(PastryNode pn, PastryRegrTest prg) {
        super(pn);
        this.prg = prg;
    }

    public int getAddress() {
        return addr;
    }

    public void sendMsg(Id nid) {
        routeMsg(nid, new RegrTestMessage(addr, getNodeHandle(), nid),
                new SendOptions());
    }

    public void sendTrace(Id nid) {
        routeMsg(nid, new RegrTestMessage(addr, getNodeHandle(), nid), new SendOptions());
    }

    /**
     * Makes sure the message was delivered to the correct node by crossrefrencing the
     * sorted nodes list in the simulator.
     */
    public void deliver(Id key, Message msg) {
        // check if numerically closest
        RegrTestMessage rmsg = (RegrTestMessage) msg;
        Id localId = getNodeId();

        if (localId != key) {
            int inBetween;
            if (localId.compareTo(key) < 0) {
                int i1 = prg.pastryNodesSortedReady.subMap(localId, key).size();
                int i2 = prg.pastryNodesSortedReady.tailMap(key).size()
                        + prg.pastryNodesSortedReady.headMap(localId).size();

                inBetween = (i1 < i2) ? i1 : i2;
            } else {
                int i1 = prg.pastryNodesSortedReady.subMap(key, localId).size();
                int i2 = prg.pastryNodesSortedReady.tailMap(localId).size()
                        + prg.pastryNodesSortedReady.headMap(key).size();

                inBetween = (i1 < i2) ? i1 : i2;
            }

            if (inBetween > 1) {
                System.out.println("messageForAppl failure, inBetween=" + inBetween);
                System.out.print(msg);
                System.out.println(" received at " + getNodeId());
                System.out.println(getLeafSet());
            }
        }
    }

    public void forward(RouteMessage rm) {

        Message msg;
        try {
            msg = rm.unwrap(deserializer);
        } catch (IOException ioe) {
            throw new RuntimeException("Error deserializing message " + rm, ioe);
        }
        Id key = rm.getTarget();
        Id nextHop = rm.getNextHop().getNodeId();

        Id localId = getNodeId();
        Id.Distance dist = localId.distance(key);
        int base = getRoutingTable().baseBitLength();

        if (prg.lastMsg == msg) {
            int localIndex = localId.indexOfMSDD(key, base);
            int lastIndex = prg.lastNode.indexOfMSDD(key, base);

            if ((localIndex > lastIndex && nextHop != localId) || (localIndex == lastIndex && dist.compareTo(prg.lastDist) > 0)) {
                System.out.println("at... " + getNodeId()
                        + " enrouteMessage failure with " + msg + " lastNode="
                        + prg.lastNode + " lastDist=" + prg.lastDist + " dist=" + dist
                        + " nextHop=" + nextHop + " loci=" + localIndex + " lasti="
                        + lastIndex);
            }

            prg.lastDist = dist;
        }
        prg.lastMsg = msg;
        prg.lastDist = dist;
        prg.lastNode = localId;
    }

    public void update(NodeHandle nh, boolean wasAdded) {
        final Id nid = nh.getNodeId();

        if (!prg.pastryNodesSorted.containsKey(nid) && nh.isAlive()) {
            System.out.println("at... " + getNodeId()
                    + "leafSetChange failure 1 with " + nid);
        }

        Id localId = thePastryNode.getNodeId();

        if (localId == nid) {
            System.out.println("at... " + getNodeId()
                    + "leafSetChange failure 2 with " + nid);
        }

        int inBetween;

        if (localId.compareTo(nid) < 0) { // localId < nid?
            int i1 = prg.pastryNodesSorted.subMap(localId, nid).size();
            int i2 = prg.pastryNodesSorted.tailMap(nid).size()
                    + prg.pastryNodesSorted.headMap(localId).size();

            inBetween = (i1 < i2) ? i1 : i2;
        } else {
            int i1 = prg.pastryNodesSorted.subMap(nid, localId).size();
            int i2 = prg.pastryNodesSorted.tailMap(localId).size()
                    + prg.pastryNodesSorted.headMap(nid).size();

            inBetween = (i1 < i2) ? i1 : i2;
        }

        int lsSize = getLeafSet().maxSize() / 2;

        if ((inBetween <= lsSize || !wasAdded || prg.pastryNodesLastAdded.contains(thePastryNode) || prg.inConcJoin)) {
            if ((inBetween <= lsSize && !wasAdded && !getLeafSet().member(nh))) {
                nh.getNodeId();
            }
        }
    }

    public void routeSetChange(NodeHandle nh, boolean wasAdded) {
        Id nid = nh.getNodeId();

        if (!prg.pastryNodesSorted.containsKey(nid)) {
            if (nh.isAlive() || wasAdded) {
                System.out.println("at... " + getNodeId()
                        + "routeSetChange failure 1 with " + nid + " wasAdded=" + wasAdded);
            }
        }

    }

    /**
     * Invoked when the Pastry node has joined the overlay network and is ready to
     * send and receive messages
     */
    public void notifyReady() {
    }

    public PastryNode getPastryNode() {
        return thePastryNode;
    }

    private static class RTAddress {
        private static int myCode = 0x9219d6ff;

        public static int getCode() {
            return myCode;
        }
    }
}
