package org.urajio.freshpastry.rice.pastry.leafset;

import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.io.Serializable;

/**
 * Initiate leaf set maintenance on the local node.
 * 
 * @version $Id: InitiateLeafSetMaintenance.java,v 1.3 2005/03/11 00:58:04 jeffh
 *          Exp $
 * 
 * @author Peter Druschel
 */

public class InitiateLeafSetMaintenance extends Message implements Serializable {

  /**
   * Constructor.
   *  
   */

  public InitiateLeafSetMaintenance() {
    super(LeafSetProtocolAddress.getCode());
  }

}