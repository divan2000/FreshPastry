package org.urajio.freshpastry.rice.pastry.join;

import org.urajio.freshpastry.rice.pastry.NodeHandle;

import java.util.Collection;

public interface JoinProtocol {
    void initiateJoin(Collection<NodeHandle> bootstrap);


//  @Override
//  public void initiateJoin(Collection<NodeHandle> bootstrap) {
//    if (logger.level <= Logger.CONFIG) logger.log(
//      "initiateJoin("+bootstrap+")");
//    if (bootstrap == null || bootstrap.isEmpty()) {      
//      // no bootstrap node, so ready immediately
//      thePastryNode.setReady();
//    } else {      
//      // schedule (re-)transmission of the join message at an exponential backoff
//      joinEvent = scheduleMsgExpBackoff(new InitiateJoin(bootstrap), 0, 2000, 2, 60000);
//    }
//  }

//  // join retransmission stuff
//  protected ScheduledMessage joinEvent;
//  @Override
//  public void nodeIsReady() {
//    if (joinEvent != null) {
//      joinEvent.cancel();
//      joinEvent = null;
//    }
//    // cancel join retransmissions
//  }


}
