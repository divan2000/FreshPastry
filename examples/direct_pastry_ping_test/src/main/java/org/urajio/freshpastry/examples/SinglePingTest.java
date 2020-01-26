package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.examples.direct.DirectPastryNodeFactory;
import org.urajio.freshpastry.examples.direct.EuclideanNetwork;
import org.urajio.freshpastry.examples.direct.NetworkSimulator;
import org.urajio.freshpastry.examples.direct.TestRecord;
import org.urajio.freshpastry.examples.standard.RandomNodeIdFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * SinglePingTest
 * <p>
 * A performance test suite for pastry.
 *
 * @author Rongmei Zhang
 * @version $Id$
 */

public class SinglePingTest {
  private DirectPastryNodeFactory factory;

  private NetworkSimulator simulator;

  private TestRecord testRecord;

  private Vector pastryNodes;

  private Vector pingClients;

  private Environment environment;

  public SinglePingTest(TestRecord tr, Environment env) {
    environment = env;
    simulator = new EuclideanNetwork(env); //SphereNetwork();
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(environment), simulator, env);
    simulator.setTestRecord(tr);
    testRecord = tr;

    pastryNodes = new Vector();
    pingClients = new Vector();
  }

  private NodeHandle getBootstrap() {
    NodeHandle bootstrap = null;
    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }
    return bootstrap;
  }

  public PastryNode makePastryNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    Ping pc = new Ping(pn);
    pingClients.addElement(pc);

    long start = System.currentTimeMillis();
    synchronized (pn) {
      while (!pn.isReady()) {
        try {
          pn.wait(300);
        } catch (InterruptedException ignored) {
        }
      }
    }
    long now = System.currentTimeMillis();
    if (now - start > 10000) {
      System.out.println("Took " + (now - start) + " to create node " + pn);
    }

    return pn;
  }

  public void sendPings(int k) {
    int n = pingClients.size();

    for (int i = 0; i < k; i++) {
      int from = environment.getRandomSource().nextInt(n);
      int to = environment.getRandomSource().nextInt(n);

      Ping pc = (Ping) pingClients.get(from);
      PastryNode pn = (PastryNode) pastryNodes.get(to);

      pc.sendPing(pn.getNodeId());
      while (simulate()) ;
    }
  }

  public boolean simulate() {
    return false;
  }

  public void checkRoutingTable() {
    int i;
    Date prev = new Date();

    for (i = 0; i < testRecord.getNodeNumber(); i++) {
      PastryNode pn = makePastryNode();
      while (simulate()) ;
      System.out.println(pn.getLeafSet());

      if (i != 0 && i % 1000 == 0)
        System.out.println(i + " nodes constructed");
    }
    System.out.println(i + " nodes constructed");

    Date curr = new Date();
    long msec = curr.getTime() - prev.getTime();
    System.out.println("time used " + (msec / 60000) + ":" + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));
  }

  public void test() {
    int i;
    long prev = environment.getTimeSource().currentTimeMillis();

    System.out.println("-------------------------");
    for (i = 0; i < testRecord.getNodeNumber(); i++) {
      PastryNode pn = makePastryNode();

      synchronized (pn) {
        while (!pn.isReady()) {
          try {
            pn.wait();
          } catch (InterruptedException ignored) {
          }
        }
      }

      if (i != 0 && i % 100 == 0) {
        System.out.println(i + " nodes constructed");
      }
    }
    System.out.println(i + " nodes constructed");

    long curr = environment.getTimeSource().currentTimeMillis();
    long msec = curr - prev;
    System.out.println("time used " + (msec / 60000) + ":"
            + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));
    prev = curr;

    sendPings(testRecord.getTestNumber());
    System.out.println(testRecord.getTestNumber() + " lookups done");

    curr = environment.getTimeSource().currentTimeMillis();
    msec = curr - prev;
    System.out.println("time used " + (msec / 60000) + ":"
            + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));

    testRecord.doneTest();
  }
}

