package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.MessageDeserializer;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.messaging.PJavaSerializedDeserializer;
import org.urajio.freshpastry.rice.pastry.routing.*;

import java.io.IOException;

/**
 * An implementation of a simple route set protocol.
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 * @version $Id: StandardRouteSetProtocol.java,v 1.15 2005/03/11 00:58:02 jeffh
 * Exp $
 */

public class StandardRouteSetProtocol extends PastryAppl implements RouteSetProtocol {
    private final static Logger logger = LoggerFactory.getLogger(StandardRouteSetProtocol.class);

    private final int maxTrials;

    private RoutingTable routeTable;

    private Environment environmet;

    public StandardRouteSetProtocol(PastryNode ln, RoutingTable rt) {
        this(ln, rt, null);
    }

    public StandardRouteSetProtocol(PastryNode ln, RoutingTable rt, MessageDeserializer md) {
        super(ln, null, RouteProtocolAddress.getCode(), md == null ? new SRSPDeserializer(ln) : md);
        this.environmet = ln.getEnvironment();
        maxTrials = (1 << rt.baseBitLength()) / 2;
        routeTable = rt;
    }

    /**
     * Receives a message.
     *
     * @param msg the message.
     */

    public void messageForAppl(Message msg) {
        if (msg instanceof BroadcastRouteRow) {
            BroadcastRouteRow brr = (BroadcastRouteRow) msg;
            logger.debug("Received " + brr.toStringFull());

            RouteSet[] row = brr.getRow();

            NodeHandle nh = brr.from();
            if (nh.isAlive())
                routeTable.put(nh);

            for (RouteSet rs : row) {
                for (int j = 0; rs != null && j < rs.size(); j++) {
                    nh = rs.get(j);
                    if (nh.isAlive() == false)
                        continue;
                    routeTable.put(nh);
                }
            }
        } else if (msg instanceof RequestRouteRow) { // a remote node request one of
            // our routeTable rows
            RequestRouteRow rrr = (RequestRouteRow) msg;

            int reqRow = rrr.getRow();
            NodeHandle nh = rrr.returnHandle();

            RouteSet[] row = routeTable.getRow(reqRow);
            BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);
            logger.debug("Responding to " + rrr + " with " + brr.toStringFull());
            thePastryNode.send(nh, brr, null, options);
        } else if (msg instanceof InitiateRouteSetMaintenance) { // request for
            // routing table
            // maintenance

            // perform routing table maintenance
            maintainRouteSet();

        } else
            throw new Error(
                    "StandardRouteSetProtocol: received message is of unknown type");

    }

    /**
     * performs periodic maintenance of the routing table for each populated row
     * of the routing table, it picks a random column and swaps routing table rows
     * with the closest entry in that column
     */

    private void maintainRouteSet() {

        logger.info("maintainRouteSet " + thePastryNode.getLocalHandle().getNodeId());

        // for each populated row in our routing table
        for (short i = (short) (routeTable.numRows() - 1); i >= 0; i--) {
            RouteSet[] row = routeTable.getRow(i);
            BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);
            RequestRouteRow rrr = new RequestRouteRow(thePastryNode.getLocalHandle(), i);
            int myCol = thePastryNode.getLocalHandle().getNodeId().getDigit(i,
                    routeTable.baseBitLength());
            int j;

            // try up to maxTrials times to find a column with live entries
            for (j = 0; j < maxTrials; j++) {
                // pick a random column
                int col = environmet.getRandomSource().nextInt(routeTable.numColumns());
                if (col == myCol)
                    continue;

                RouteSet rs = row[col];

                // swap row with closest node only

                if (rs != null && rs.size() > 0) {
                    NodeHandle nh;

                    nh = rs.closestNode(10); // any liveness status will work

                    // this logic is to make this work correctly in the simulator
                    // if we find him not alive, then we would have routed to him and the liveness would have failed
                    // thus the correct behavior is to break, not continue.  In other words, we waste this cycle
                    // finding the node faulty and removing it rather than doing an actual swap
                    // - Jeff Hoye,  Aug 9, 2006
                    if (!nh.isAlive()) {
                        logger.debug("found dead node in table:" + nh);
                        rs.remove(nh);
                        break;
                    }

                    logger.debug("swapping with " + (i + 1) + "/" + routeTable.numRows() + " " + (j + 1) + "/" + maxTrials + ":" + nh);
                    thePastryNode.send(nh, brr, null, options);
                    thePastryNode.send(nh, rrr, null, options);
                    break;
                }
            }

            // once we hit a row where we can't find a populated entry after numTrial
            // trials, we finish
            if (j == maxTrials)
                break;

        }

    }

    public boolean deliverWhenNotReady() {
        return true;
    }

    static class SRSPDeserializer extends PJavaSerializedDeserializer {
        public SRSPDeserializer(PastryNode pn) {
            super(pn);
        }

        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            switch (type) {
                case RequestRouteRow.TYPE:
                    return new RequestRouteRow(sender, buf);
                case BroadcastRouteRow.TYPE:
                    return new BroadcastRouteRow(buf, pn, pn);
            }
            return null;
        }
    }
}
