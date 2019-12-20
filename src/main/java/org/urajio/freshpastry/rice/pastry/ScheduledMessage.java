package org.urajio.freshpastry.rice.pastry;

import rice.environment.logging.Logger;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.selector.TimerTask;

/**
 * A class that represents scheduled message events
 * 
 * @version $Id: ScheduledMessage.java 2808 2005-11-22 15:38:49 +0100 (Tue, 22
 *          Nov 2005) jeffh $
 * 
 * @author Peter Druschel
 */
public class ScheduledMessage extends TimerTask {
  protected PastryNode localNode;

  protected Message msg;

  /**
   * Constructor
   * 
   * @param the
   *          message
   */
  public ScheduledMessage(PastryNode pn, Message msg) {
    localNode = pn;
    this.msg = msg;
  }

  /**
   * Returns the message
   * 
   * @return the message
   */
  public Message getMessage() {
    return msg;
  }

  public PastryNode getLocalNode() {
    return localNode;
  }

  /**
   * deliver the message
   */
  public void run() {
    try {
      // timing with cancellation
      Message m = msg;
      if (m != null) localNode.receiveMessage(msg);
    } catch (Exception e) {
      Logger logger = localNode.getEnvironment().getLogManager().getLogger(getClass(), null);
      if (logger.level <= Logger.WARNING) logger.logException("Delivering " + this + " caused exception ", e);
    }
  }

  public String toString() {
    return "SchedMsg for " + msg;
  }

  /*
   * (non-Javadoc)
   * 
   * @see rice.p2p.commonapi.CancellableTask#cancel()
   */
  public boolean cancel() {
    // memory management
    msg = null;
    localNode = null;
    return super.cancel();
  }

}
