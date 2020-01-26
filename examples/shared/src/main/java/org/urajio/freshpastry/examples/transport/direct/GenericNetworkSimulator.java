package org.urajio.freshpastry.examples.transport.direct;

import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;

public interface GenericNetworkSimulator<Identifier, MessageType> extends LivenessProvider<Identifier> {

    Environment getEnvironment();

    /**
     * Get the environment related to a specific node.
     *
     * @param i
     * @return
     */
    Environment getEnvironment(Identifier i);

    /**
     * Determines delivery time from a to b.
     *
     * @param a a node id.
     * @param b another node id.
     * @return delay of b from a.
     */
    float networkDelay(Identifier a, Identifier b);


    /**
     * Deliver message.
     */
    Cancellable deliverMessage(MessageType msg, Identifier to, Identifier from, int delay);

    /**
     * Deliver message.
     */
    CancellableTask enqueueDelivery(Delivery del, int delay);

    DirectTransportLayer<Identifier, MessageType> getTL(Identifier i);

    boolean isAlive(Identifier i);

    /**
     * Kill identifier.
     *
     * @param i
     */
    void remove(Identifier i);

    void start();

    void stop();

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
     * @param the multiple on realtime that the simulator is allowed to run at,
     *            zero or less will cause no bound on the simulation speed
     */
    void setMaxSpeed(float rate);

    /**
     * unlimited maxSpeed
     */
    void setFullSpeed();

    RandomSource getRandomSource();
}
