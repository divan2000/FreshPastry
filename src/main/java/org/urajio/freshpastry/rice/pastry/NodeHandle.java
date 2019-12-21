package org.urajio.freshpastry.rice.pastry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawSerializable;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observer;


/**
 * Interface for handles to remote nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */
public abstract class NodeHandle extends org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle implements RawSerializable
{
  private final static transient Logger logger = LoggerFactory.getLogger(NodeHandle.class);

  public static final int LIVENESS_ALIVE = 1;
  public static final int LIVENESS_SUSPECTED = 2;
  public static final int LIVENESS_DEAD = 3;
    
  // the local pastry node
  protected transient PastryNode localnode;

  static final long serialVersionUID = 987479397660721015L;
  /**
   * Gets the nodeId of this Pastry node.
   *
   * @return the node id.
   */
  public abstract Id getNodeId();

  public org.urajio.freshpastry.rice.p2p.commonapi.Id getId() {
    return getNodeId();
  }

  /**
   * Returns the last known liveness information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * @return true if the node is alive, false otherwise.
   * 
   * @deprecated use PastryNode.isAlive(NodeHandle) 
   */
  public final boolean isAlive() {
    return getLiveness() < LIVENESS_DEAD; 
  }

  /**
   * A more detailed version of isAlive().  This can return 3 states:
   * 
   * @return LIVENESS_ALIVE, LIVENESS_SUSPECTED, LIVENESS_DEAD
   */
  public abstract int getLiveness();
  
  /**
   * Method which FORCES a check of liveness of the remote node.  Note that
   * this method should ONLY be called by internal Pastry maintenance algorithms - 
   * this is NOT to be used by applications.  Doing so will likely cause a
   * blowup of liveness traffic.
   *
   * @return true if node is currently alive.
   */
  public boolean checkLiveness() {
    return ping();
  }

  /**
   * Returns the last known proximity information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * Smaller values imply greater proximity. The exact nature and interpretation of the proximity metric
   * implementation-specific.
   *
   * @deprecated use PastryNode.proximity() or Endpoint.proximity()
   * @return the proximity metric value
   */
  public abstract int proximity();

  /**
   * Ping the node. Refreshes the cached liveness status and proximity value of the Pastry node associated
   * with this.
   * Invoking this method causes network activity.
   *
   * @return true if node is currently alive.
   */
  public abstract boolean ping();

  /**
   * Accessor method.
   */
  public final PastryNode getLocalNode() {
    return localnode;
  }

//  transient Exception ctor;
//  public NodeHandle() {
//    ctor = new Exception("ctor"); 
//  }
  
  /**
   * May be called from handle etc methods to ensure that local node has
   * been set, either on construction or on deserialization/receivemsg.
   */
  public void assertLocalNode() {
    if (localnode == null) {
//      ctor.printStackTrace();
      throw new RuntimeException("PANIC: localnode is null in " + this+"@"+System.identityHashCode(this));
    }
  }

  /**
   * Equality operator for nodehandles.
   *
   * @param obj a nodehandle object
   * @return true if they are equal, false otherwise.
   */
  public abstract boolean equals(Object obj);

  /**
   * Method which is used by Pastry to start the bootstrapping process on the 
   * local node using this handle as the bootstrap handle.  Default behavior is
   * simply to call receiveMessage(msg), but transport layer implementations may
   * care to perform other tasks by overriding this method, since the node is
   * not technically part of the ring yet.
   *
   * @param msg the bootstrap message.
   */
  public void bootstrap(Message msg) {
    receiveMessage(msg);
  }

  /**
   * Hash codes for nodehandles.
   *
   * @return a hash code.
   */
  public abstract int hashCode();
  
  /**
   * @deprecated use PastryNode.send() or Endpoint.send()
   * @param msg
   */
  public abstract void receiveMessage(Message msg);

  public abstract void serialize(OutputBuffer buf) throws IOException;
  
  /****************** Overriding of the Observer pattern to include priority **********/
  
  /**
   * Class which holds an observer and a priority.  For sorting.
   * @author Jeff Hoye
   */
  static class ObsPri implements Comparable<ObsPri> {
    Observer obs;
    int pri;
    
    public ObsPri(Observer o, int priority) {
      obs = o;
      pri = priority;
    }

    public int compareTo(ObsPri o) {
      int ret = ((ObsPri)o).pri - pri;
      if (ret == 0) {
        if (((ObsPri)o).equals(o)) {
          return 0;
        } else {
          return System.identityHashCode((ObsPri)o)-System.identityHashCode(this);
        }
      }
//      System.out.println(this+".compareTo("+that+"):"+ret);
      return ret;
    }
    
    public String toString() {
      return obs+":"+pri;      
    }
  }

  transient List<ObsPri> obs = new ArrayList<>();
  /**
   * Need to construct obs in deserialization.
   */
  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    obs = new ArrayList<>();
  }
  
  public void addObserver(Observer o) {
    addObserver(o, 0);  
  }
  
  /**
   * 
   * @param o
   * @param priority higher priority observers will be called first
   */
  public void addObserver(Observer o, int priority) {
    logger.debug(this+".addObserver("+o+")");
    synchronized (obs) {
      // this pass makes sure it's not already an observer, and also finds 
      // the right spot for it
      for (int i = 0; i < obs.size(); i++) {
        ObsPri op = obs.get(i);
        if (op.obs.equals(o)) {
          if (op.pri != priority) {
            // change the priority, resort, return
            // Should we warn?  Hopefully this won't happen very often...
            logger.debug(this+".addObserver("+o+","+priority+") changed priority, was:"+op);

            op.pri = priority;
            Collections.sort(obs);
            return;
          }
          // TODO: can optimize this if need be, make this look for the insertPoint.  For now, we're just going to add then sort.
//        } else {
//          if (op.pri <= priority) {
//            insertPoint = i; 
//          }
        }
      }
      obs.add(new ObsPri(o,priority));
//      logger.log(this+"addObserver("+o+")list1:"+obs);
      Collections.sort(obs);
//      logger.log(this+"addObserver("+o+")list2:"+obs);
    }
//    super.addObserver(o); 
  }
  
  public void deleteObserver(Observer o) {
    logger.debug(this+".deleteObserver("+o+")");
//    super.deleteObserver(o); 
    synchronized (obs) {
      for (int i = 0; i < obs.size(); i++) {
        ObsPri op = obs.get(i);
        if (op.obs.equals(o)) {
          logger.debug(this+".deleteObserver("+o+"):success");
          obs.remove(i);
          return;
        }
      }
      logger.debug(this+".deleteObserver("+o+"):failure "+o+" was not an observer.");
    }
  }
  
  public void notifyObservers(Object arg) {
    List<ObsPri> l;
    synchronized(obs) {
      l = new ArrayList<>(obs);
//      logger.log(this+"notifyObservers("+arg+")list1:"+obs);
//      logger.log(this+"notifyObservers("+arg+")list2:"+l);
    }
    for(ObsPri op : l) {
      logger.debug(this+".notifyObservers("+arg+"):notifying "+op);
      op.obs.update(this, arg); 
    }    
  }

  public synchronized int countObservers() {
    return obs.size();
  }

  public synchronized void deleteObservers() {
    obs.clear();
  }

  
  /**
   * Method which allows the observers of this socket node handle to be updated.
   * This method sets this object as changed, and then sends out the update.
   *
   * @param update The update
   */
  public void update(Object update) {
    logger.debug(this+".update("+update+")"+countObservers());
//    setChanged();
    notifyObservers(update);
    logger.debug(this+".update("+update+")"+countObservers()+" done");
  }
}