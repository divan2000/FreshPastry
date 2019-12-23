package org.urajio.freshpastry.rice.pastry.leafset;

import org.urajio.freshpastry.rice.p2p.commonapi.NodeHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.RangeCannotBeDeterminedException;

/**
 * @author Jeff Hoye
 */
public class LSRangeCannotBeDeterminedException extends RangeCannotBeDeterminedException {

  public int r;
  public int pos;
  public int uniqueCount;
  public NodeHandle nh;
  
  /**
   * @param string
   */
  public LSRangeCannotBeDeterminedException(String string, int r, int pos, int uniqueNodes, NodeHandle nh, LeafSet ls) {
    super(string+" replication factor:"+r+" nh position:"+pos+" handle:"+nh+" ls.uniqueNodes():"+uniqueNodes+" "+ls.toString());
    this.r = r;
    this.pos = pos;
    this.nh = nh;
    this.uniqueCount = uniqueNodes;
  }

}
