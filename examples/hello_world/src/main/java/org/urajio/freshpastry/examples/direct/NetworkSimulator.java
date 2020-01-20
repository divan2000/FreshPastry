package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.examples.transport.direct.Delivery;
import org.urajio.freshpastry.examples.transport.direct.DirectTransportLayer;
import org.urajio.freshpastry.examples.transport.direct.GenericNetworkSimulator;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;
import org.urajio.freshpastry.rice.pastry.PastryNode;

/**
 * Interface to an object which is simulating the network.
 *
 * @author Andrew Ladd
 * @version $Id$
 */
public interface NetworkSimulator<Identifier, MessageType> {

    Environment getEnvironment();


    /**
     * Checks to see if a node id is alive.
     *
     * @return true if alive, false otherwise.
     */
    boolean isAlive(Identifier nh);

    /**
     * Determines rtt between two nodes.
     *
     * @param a a node id.
     * @param b another node id.
     * @return proximity of b to a.
     */
    float proximity(Identifier a, Identifier b);

    /**
     * Determines delivery time from a to b.
     *
     * @param a a node id.
     * @param b another node id.
     * @return proximity of b to a.
     */
    float networkDelay(Identifier a, Identifier b);

    TestRecord getTestRecord();

    void setTestRecord(TestRecord tr);

    /**
     * Returns the closest Node in proximity.
     */
    DirectNodeHandle getClosest(DirectNodeHandle nh);

    void destroy(DirectPastryNode dpn);

    /**
     * Generates a random node record
     *
     * @return
     */
    NodeRecord generateNodeRecord();

    void removeNode(PastryNode node);

    void start();

    void stop();

    /**
     * Deliver message.
     */
    CancellableTask enqueueDelivery(Delivery del, int delay);

    /**
     * The max rate of the simulator compared to realtime.
     * <p>
     * The rule is that the simulated clock will not be set to a value greater
     * than the factor from system-time that the call was made.  Thus
     * <p>
     * if 1 hour ago, you said the simulator should run at 10x realtime the simulated
     * clock will only have advanced 10 hours.
     * <p>
     * Note that if the simulator cannot keep up with the system clock in the early
     * part, it may move faster than the value you set to "catch up"
     * <p>
     * To prevent this speed-up from becoming unbounded, you may wish to call
     * setMaxSpeed() periodically or immediately after periods of expensive calculations.
     * <p>
     * Setting the simulation speed to zero will not pause the simulation, you must
     * call stop() to do that.
     *
     * @param rate the multiple on realtime that the simulator is allowed to run at,
     *             zero or less will cause no bound on the simulation speed
     */
    void setMaxSpeed(float rate);

    /**
     * unlimited maxSpeed
     */
    void setFullSpeed();

    /**
     * Call this when a message is sent.
     *
     * @param m     the message
     * @param from  the source
     * @param to    the destination
     * @param delay the network proximity (when the message will be received)
     */
    void notifySimulatorListenersSent(MessageType m, Identifier from, Identifier to, int delay);

    /**
     * Call this when a message is received.
     *
     * @param m    the message
     * @param from the source
     * @param to   the destination
     */
    void notifySimulatorListenersReceived(MessageType m, Identifier from, Identifier to);

    /**
     * @param sl
     * @return true if added, false if already a listener
     */
    boolean addSimulatorListener(GenericSimulatorListener<Identifier, MessageType> sl);

    /**
     * @param sl
     * @return true if removed, false if not already a listener
     */
    boolean removeSimulatorListener(GenericSimulatorListener<Identifier, MessageType> sl);

    NodeRecord getNodeRecord(DirectNodeHandle handle);

    LivenessProvider<Identifier> getLivenessProvider();

    GenericNetworkSimulator<Identifier, MessageType> getGenericSimulator();

    void registerNode(Identifier i, DirectTransportLayer<Identifier, MessageType> dtl, NodeRecord nr);
}
