package org.urajio.freshpastry.examples.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.examples.transport.direct.Delivery;
import org.urajio.freshpastry.examples.transport.direct.DirectTransportLayer;
import org.urajio.freshpastry.examples.transport.direct.EventSimulator;
import org.urajio.freshpastry.examples.transport.direct.GenericNetworkSimulator;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.util.*;

public class BasicNetworkSimulator<Identifier, MessageType> extends EventSimulator implements
        GenericNetworkSimulator<Identifier, MessageType> {
  private final static Logger logger = LoggerFactory.getLogger(BasicNetworkSimulator.class);
  protected final int maxDiameter;
  protected final int minDelay;
  final List<LivenessListener<Identifier>> livenessListeners = new ArrayList<>();
  protected int MIN_DELAY = 1;
  /**
   * This maps to the next highest transport layer
   */
  Map<Identifier, Tupel> nodes = Collections.synchronizedMap(new HashMap<Identifier, Tupel>());
  // to notify of send/receive
  NetworkSimulator<Identifier, MessageType> sim;

  public BasicNetworkSimulator(Environment env, RandomSource random, NetworkSimulator<Identifier, MessageType> sim) {
    super(env, random);
    this.sim = sim;
    manager.useLoopListeners(false);
    Parameters params = env.getParameters();
    maxDiameter = params.getInt("pastry_direct_max_diameter");
    minDelay = params.getInt("pastry_direct_min_delay");
    start();
  }

  private void addTask(TimerTask dtt) {
    logger.debug("addTask(" + dtt + ")");

    manager.getTimer().schedule(dtt);

  }

  public CancellableTask enqueueDelivery(Delivery d, int delay) {
    long time = timeSource.currentTimeMillis() + delay;
    logger.debug("BNS: enqueueDelivery " + d + ":" + time);
    DeliveryTimerTask dtt = null;
    dtt = new DeliveryTimerTask(d, time, d.getSeq());
    addTask(dtt);
    return dtt;
  }

  /**
   * node should always be a local node, because this will be delivered instantly
   */
  public Cancellable deliverMessage(MessageType msg, Identifier node, Identifier from) {
    return deliverMessage(msg, node, from, 0);
  }

  public Cancellable deliverMessage(MessageType msg, Identifier node, Identifier from,
                                    int delay) {
    if (delay > 0) {
      sim.notifySimulatorListenersSent(msg, from, node, delay);
    }
    return deliverMessage(msg, node, from, delay, 0);
  }

  void notifySimulatorListenersReceived(MessageType m, Identifier from, Identifier to) {
    sim.notifySimulatorListenersReceived(m, from, to);
  }

  public Cancellable deliverMessageFixedRate(MessageType msg,
                                             Identifier node, Identifier from, int delay, int period) {
    return deliverMessage(msg, node, from, delay, period);
  }

  public Cancellable deliverMessage(MessageType msg, Identifier node, Identifier from, int delay, int period) {
    logger.debug("BNS: deliver " + msg + " to " + node);

    DirectTimerTask dtt = null;

    if (from == null || isAlive(from)) {
      MessageDelivery<Identifier, MessageType> md = new MessageDelivery<>(msg, node, from, null, this);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay, period);
      addTask(dtt);
    }
    return dtt;
  }


  public void registerIdentifier(Identifier i, DirectTransportLayer<Identifier, MessageType> dtl, NodeRecord record) {
    //logger.log("registerIdentifier("+i+") on thread "+Thread.currentThread());
    nodes.put(i, new Tupel(i, dtl, record));
  }

  public void remove(Identifier i) {
    if (!environment.getSelectorManager().isSelectorThread())
      throw new IllegalStateException("Operation not permitted on non-selector thread.");
    nodes.remove(i);
    notifyLivenessListeners(i, LivenessListener.LIVENESS_DEAD, null);
  }

  public Environment getEnvironment() {
    return environment;
  }

  public Environment getEnvironment(Identifier i) {
    return nodes.get(i).tl.getEnvironment();
  }

  public RandomSource getRandomSource() {
    return random;
  }

  public boolean isAlive(Identifier i) {
    return nodes.containsKey(i);
  }

  public DirectTransportLayer<Identifier, MessageType> getTL(Identifier i) {
    Tupel t = nodes.get(i);
    if (t == null) return null;
    return t.tl;
  }

  /**
   * computes the one-way distance between two NodeIds
   *
   * @param a the first NodeId
   * @param b the second NodeId
   * @return the proximity between the two input NodeIds
   */
  public float networkDelay(Identifier a, Identifier b) {
    Tupel ta = nodes.get(a);
    Tupel tb = nodes.get(b);
    if (ta == null) {
      throw new RuntimeException("asking about node proximity for unknown node " + a);
    }

    if (tb == null) {
      throw new RuntimeException("asking about node proximity for unknown node " + b);
    }

    NodeRecord nra = ta.record;
    NodeRecord nrb = tb.record;

    return nra.networkDelay(nrb);
  }

  /**
   * computes the rtt between two NodeIds
   *
   * @param a the first NodeId
   * @param b the second NodeId
   * @return the proximity between the two input NodeIds
   */
  public float proximity(Identifier a, Identifier b) {
    Tupel ta = nodes.get(a);
    Tupel tb = nodes.get(b);
    if (ta == null) {
      throw new RuntimeException("asking about node proximity for unknown node " + a + " " + b);
    }

    if (tb == null) {
      throw new RuntimeException("asking about node proximity for unknown node " + b + " " + a);
    }

    NodeRecord nra = ta.record;
    NodeRecord nrb = tb.record;

    return nra.proximity(nrb);
  }

  public NodeRecord getNodeRecord(DirectNodeHandle handle) {
    Tupel t = nodes.get(handle);
    if (t == null) return null;
    return t.record;
  }

  public void addLivenessListener(LivenessListener<Identifier> name) {
    synchronized (livenessListeners) {
      livenessListeners.add(name);
    }
  }

  public boolean removeLivenessListener(LivenessListener<Identifier> name) {
    synchronized (livenessListeners) {
      return livenessListeners.remove(name);
    }
  }

  protected void notifyLivenessListeners(Identifier i, int liveness, Map<String, Object> options) {
    logger.debug("notifyLivenessListeners(" + i + "," + liveness + "):" + livenessListeners.get(0));
    List<LivenessListener<Identifier>> temp;
    synchronized (livenessListeners) {
      temp = new ArrayList<>(livenessListeners);
    }
    for (LivenessListener<Identifier> listener : temp) {
      listener.livenessChanged(i, liveness, options);
    }
  }

  public boolean checkLiveness(Identifier i, Map<String, Object> options) {
    return false;
  }

  public int getLiveness(Identifier i, Map<String, Object> options) {
    if (nodes.containsKey(i)) {
      return LivenessListener.LIVENESS_ALIVE;
    }
    return LivenessListener.LIVENESS_DEAD;
  }

  public void clearState(Identifier i) {
    throw new IllegalStateException("not implemented");
  }

  class Tupel {
    Identifier i;
    DirectTransportLayer<Identifier, MessageType> tl;
    NodeRecord record;

    public Tupel(Identifier i, DirectTransportLayer<Identifier, MessageType> tl, NodeRecord record) {
      super();
      this.i = i;
      this.tl = tl;
      this.record = record;
    }
  }
}
