package org.urajio.freshpastry.rice.pastry.standard;

import org.urajio.freshpastry.rice.pastry.leafset.LeafSetProtocolAddress;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.io.Serializable;

/**
 * Initiate leaf set maintenance on the local node.
 *
 * @author Peter Druschel
 * @version $Id$
 */

public class InitiatePingNeighbor extends Message implements Serializable {

    public InitiatePingNeighbor() {
        super(LeafSetProtocolAddress.getCode());
    }
}