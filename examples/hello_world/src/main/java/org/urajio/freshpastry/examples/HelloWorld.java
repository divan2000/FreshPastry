package org.urajio.freshpastry.examples;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.examples.direct.DirectPastryNodeFactory;
import org.urajio.freshpastry.examples.direct.EuclideanNetwork;
import org.urajio.freshpastry.examples.direct.NetworkSimulator;
import org.urajio.freshpastry.examples.standard.RandomNodeIdFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.PastryNodeFactory;

import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * A hello world example for pastry. This is the "direct" driver.
 *
 * @author Sitaram Iyer
 * @version $Id$
 */
public class HelloWorld {
  private final static Logger logger = LoggerFactory.getLogger(HelloWorld.class);

  private static int numnodes = 3;
  private static int nummsgs = 30; // total messages
  private static boolean simultaneous_joins = false;
  private static boolean simultaneous_msgs = false;
  Environment environment;
  private PastryNodeFactory factory;
  private NetworkSimulator simulator;
  private Vector pastryNodes;
  private Vector helloClients;

  /**
   * Constructor
   */
  public HelloWorld(Environment env) {
    environment = env;
    simulator = new EuclideanNetwork(env);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(environment), simulator, environment);

    pastryNodes = new Vector();
    helloClients = new Vector();
  }

  private static void doIinitstuff(String[] args, Environment env) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-verbosity") && i + 1 < args.length) {
        int num = Integer.parseInt(args[i + 1]);
        env.getParameters().setInt("loglevel", num * 100);
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-silent") && i + 1 < args.length) {
        env.getParameters().setInt("loglevel", Level.ERROR_INT);
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-verbose") && i + 1 < args.length) {
        env.getParameters().setInt("loglevel", Level.ALL_INT);
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i + 1 < args.length)
        numnodes = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-msgs") && i + 1 < args.length)
        nummsgs = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-simultaneous_joins"))
        simultaneous_joins = true;

      if (args[i].equals("-simultaneous_msgs"))
        simultaneous_msgs = true;

      if (args[i].equals("-help")) {
        System.out
                .println("Usage: HelloWorld [-msgs m] [-nodes n] [-verbose|-silent|-verbosity v]");
        System.out
                .println("                  [-simultaneous_joins] [-simultaneous_msgs] [-help]");
        System.out
                .println("  Default verbosity is 8, -verbose is 1, and -silent is 10 (error msgs only).");
        System.exit(1);
      }
    }
  }

  /**
   * Usage: HelloWorld [-msgs m] [-nodes n] [-verbose|-silent|-verbosity v]
   * [-simultaneous_joins] [-simultaneous_msgs] [-help]
   */
  public static void main(String[] args) {
    Environment env = Environment.directEnvironment();

//    env.getParameters().setInt("loglevel", 800);
    doIinitstuff(args, env);

    HelloWorld driver = new HelloWorld(env);

    for (int i = 0; i < numnodes; i++) {
      driver.makePastryNode(i);
      if (simultaneous_joins == false)
        while (driver.simulate())
          ;
    }
    if (simultaneous_joins) {
      System.out.println("let the joins begin!");
      while (driver.simulate())
        ;
    }

    System.out.println(numnodes + " nodes constructed");

    driver.printLeafSets();

    for (int i = 0; i < nummsgs; i++) {
      driver.sendRandomMessage();
      if (simultaneous_msgs == false)
        while (driver.simulate())
          ;
    }

    if (simultaneous_msgs) {
      System.out.println("let the msgs begin!");
      while (driver.simulate())
        ;
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException ie) {
    }
    env.destroy();
  }

  /**
   * Get a handle to a bootstrap node. This is only a simulation, so we pick the
   * most recently created node.
   *
   * @return handle to bootstrap node, or null.
   */
  private NodeHandle getBootstrap() {
    NodeHandle bootstrap = null;
    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }
    return bootstrap;
  }

  /**
   * Create a Pastry node and add it to pastryNodes. Also create a client
   * application for this node.
   */
  public void makePastryNode(int num) {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    HelloWorldApp app = new HelloWorldApp(pn);
    helloClients.addElement(app);

    synchronized (pn) {
      while (!pn.isReady()) {
        try {
          pn.wait(300);
        } catch (InterruptedException ie) {
        }
      }
    }

    System.out.println("created " + num + " " + pn);
  }

  /**
   * Print leafsets of all nodes in pastryNodes.
   */
  private void printLeafSets() {
    for (Object pastryNode : pastryNodes) {
      PastryNode pn = (PastryNode) pastryNode;
      System.out.println(pn.getLeafSet().toString());
    }
  }

  /**
   * Invoke a HelloWorldApp method called sendRndMsg. First choose a random
   * application from helloClients.
   */
  private void sendRandomMessage() {
    int n = helloClients.size();
    int client = environment.getRandomSource().nextInt(n);
    HelloWorldApp app = (HelloWorldApp) helloClients.get(client);
    app.sendRndMsg(environment.getRandomSource());
  }

  /**
   * Process one message.
   */
  private boolean simulate() {
    return false;
  }
}
