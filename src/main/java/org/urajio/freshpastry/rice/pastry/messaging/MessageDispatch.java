package org.urajio.freshpastry.rice.pastry.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.client.PastryAppl;
import org.urajio.freshpastry.rice.pastry.transport.Deserializer;

import java.util.HashMap;

/**
 * An object which remembers the mapping from names to MessageReceivers
 * and dispatches messages by request.
 * 
 * For consistent routing, modified to only deliver messages to applications 
 * if the PastryNode.isReady().  It will still deliver messages to any non-PastryAppl
 * because these "services" may be needed to boot the node into the ring.  Any 
 * messages to a PastryAppl will be buffered until the node goes ready.
 * 
 * TODO:  We need to make it explicit which apps can receive messages before
 * PastryNode.isReady().
 * 
 * @version $Id$
 *
 * @author Jeff Hoye
 * @author Andrew Ladd
 */

public class MessageDispatch implements Destructable {
  private final static Logger logger = LoggerFactory.getLogger(MessageDispatch.class);

  // have modified from HashMap to HashMap to use the internal representation
  // of a LocalAddress.  Otherwise remote node cannot get its message delivered
  // because objects constructed differently are not mapped to the same value
  private HashMap<Integer,PastryAppl> addressBook;

  protected PastryNode localNode;

  /**
   * Also held by the transport layer to allow it to deserialize the messages.
   */
  protected Deserializer deserializer;
  
  /**
   * Constructor.
   */
  public MessageDispatch(PastryNode pn, Deserializer deserializer) {
    this.deserializer = deserializer;
    addressBook = new HashMap<>();
    this.localNode = pn;
  }

  /**
   * Registers a receiver with the mail service.
   *
   * @param address a name for a receiver.
   * @param receiver the receiver.
   */
  public void registerReceiver(int address, PastryAppl receiver) {
    // the stack trace is to figure out who registered for what, it is not an error
    

    logger.debug("Registering "+receiver+" for address " + address);
    logger.debug("Registering receiver for address " + address, new Exception("stack trace"));
    if (addressBook.get(address) != null) {
      throw new IllegalArgumentException("Registering receiver for already-registered address " + address);
    }

    deserializer.setDeserializer(address, receiver.getDeserializer());
    addressBook.put(address, receiver);
  }
  
  public PastryAppl getDestination(Message msg) {
    return getDestinationByAddress(msg.getDestination());    
  }

  public PastryAppl getDestinationByAddress(int addr) {
    return addressBook.get(addr);
  }

  /**
   * Dispatches a message to the appropriate receiver.
   * 
   * It will buffer the message under the following conditions:
   *   1) The MessageReceiver is not yet registered.
   *   2) The MessageReceiver is a PastryAppl, and localNode.isReady() == false
   *
   * @param msg the message.
   *
   * @return true if message could be dispatched, false otherwise.
   */
  public boolean dispatchMessage(Message msg) {
    if (msg.getDestination() == 0) {
      logger.warn("Message "+msg+","+msg.getClass().getName()+" has no destination.", new Exception("Stack Trace"));
      return false;
    }
    // NOTE: There is no safety issue with calling localNode.isReady() because this is on the 
    // PastryThread, and the only way to set a node ready is also on the ready thread.
    PastryAppl mr = addressBook.get(msg.getDestination());

    if (mr == null) {
        logger.debug("Dropping message " + msg + " because the application address " + msg.getDestination() + " is unknown.");
      return false;
    } else {
      mr.receiveMessage(msg); 
      return true;
    }
  }  
  
//  public boolean dispatchMessage(RawMessageDelivery msg) {
//    if (msg.getAddress() == 0) {
//      Logger logger = localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null);
//      if (logger.level <= Logger.WARNING) logger.logException(
//          "Message "+msg+","+msg.getClass().getName()+" has no destination.", new Exception("Stack Trace"));
//      return false;
//    }
//    // NOTE: There is no safety issue with calling localNode.isReady() because this is on the 
//    // PastryThread, and the only way to set a node ready is also on the ready thread.
//    PastryAppl mr = (PastryAppl) addressBook.get(Integer.valueOf(msg.getAddress()));
//
//    if (mr == null) {
//      if (logger.level <= Logger.WARNING) logger.log(
//          "Dropping message " + msg + " because the application address " + msg.getAddress() + " is unknown.");
//      return false;
//    } else {
//      mr.receiveMessageInternal(msg); 
//      return true;
//    }
//  }  
  
  public void destroy() {
    for (PastryAppl mr : addressBook.values()) {
      logger.info("Destroying " + mr);
      mr.destroy();
    }      
    addressBook.clear();
  }
}
