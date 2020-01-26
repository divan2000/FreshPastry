package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.examples.transport.direct.Delivery;
import org.urajio.freshpastry.examples.transport.direct.DirectTransportLayer;
import org.urajio.freshpastry.examples.transport.direct.GenericNetworkSimulator;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.environment.random.simple.SimpleRandomSource;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.util.ArrayList;
import java.util.List;

public class NetworkSimulatorImpl<Identifier, MessageType> implements NetworkSimulator<Identifier, MessageType> {
    /************** SimulatorListeners handling *******************/
    final List<GenericSimulatorListener<Identifier, MessageType>> listeners = new ArrayList<>();
    protected BasicNetworkSimulator<Identifier, MessageType> simulator;
    protected RandomSource random;
    protected ProximityGenerator generator;
    protected LivenessProvider<Identifier> livenessProvider;
    // ************************* What is this? **********************
    private TestRecord testRecord;

    // TODO: add listener to top level tl, to notify simulator listeners
    public NetworkSimulatorImpl(Environment env, ProximityGenerator generator) {
        Parameters params = env.getParameters();
        if (params.contains("pastry_direct_use_own_random")
                && params.getBoolean("pastry_direct_use_own_random")) {

            if (params.contains("pastry_direct_random_seed")
                    && !params.getString("pastry_direct_random_seed").equalsIgnoreCase(
                    "clock")) {
                this.random = new SimpleRandomSource(params.getLong("pastry_direct_random_seed"));
            } else {
                this.random = new SimpleRandomSource();
            }
        } else {
            this.random = env.getRandomSource();
        }
        generator.setRandom(random);
        this.generator = generator;
        simulator = new BasicNetworkSimulator<>(env, random, this);
        livenessProvider = simulator;
    }

    // ****************** passtrhougs to simulator ***************
    public Environment getEnvironment() {
        return simulator.getEnvironment();
    }

    public void setFullSpeed() {
        simulator.setFullSpeed();
    }

    public void setMaxSpeed(float rate) {
        simulator.setMaxSpeed(rate);
    }

    public void start() {
        simulator.start();
    }

    public void stop() {
        simulator.stop();
    }

    /**
     * get TestRecord
     *
     * @return the returned TestRecord
     */
    public TestRecord getTestRecord() {
        return testRecord;
    }

    /**
     * set TestRecord
     *
     * @param tr input TestRecord
     */
    public void setTestRecord(TestRecord tr) {
        testRecord = tr;
    }

    public boolean addSimulatorListener(GenericSimulatorListener<Identifier, MessageType> sl) {
        synchronized (listeners) {
            if (listeners.contains(sl)) return false;
            listeners.add(sl);
            return true;
        }
    }

    public boolean removeSimulatorListener(GenericSimulatorListener<Identifier, MessageType> sl) {
        synchronized (listeners) {
            return listeners.remove(sl);
        }
    }

    public void notifySimulatorListenersSent(MessageType m, Identifier from, Identifier to, int delay) {
        List<GenericSimulatorListener<Identifier, MessageType>> temp;

        // so we aren't holding a lock while iterating/calling
        synchronized (listeners) {
            temp = new ArrayList<>(listeners);
        }

        for (GenericSimulatorListener<Identifier, MessageType> listener : temp) {
            listener.messageSent(m, from, to, delay);
        }
    }

    public void notifySimulatorListenersReceived(MessageType m, Identifier from, Identifier to) {
        List<GenericSimulatorListener<Identifier, MessageType>> temp;

        // so we aren't holding a lock while iterating/calling
        synchronized (listeners) {
            temp = new ArrayList<>(listeners);
        }

        for (GenericSimulatorListener<Identifier, MessageType> listener : temp) {
            listener.messageReceived(m, from, to);
        }
    }

    public void destroy(DirectPastryNode dpn) {
    }

    public CancellableTask enqueueDelivery(Delivery del, int delay) {
        return null;
    }

    public NodeRecord generateNodeRecord() {
        return generator.generateNodeRecord();
    }

    public DirectNodeHandle getClosest(DirectNodeHandle nh) {
        return null;
    }

    public boolean isAlive(Identifier nh) {
        return simulator.isAlive(nh);
    }

    public float networkDelay(Identifier a, Identifier b) {
        return simulator.networkDelay(a, b);
    }

    public float proximity(Identifier a, Identifier b) {
        return simulator.proximity(a, b);
    }

    public void removeNode(PastryNode node) {
    }

    public NodeRecord getNodeRecord(DirectNodeHandle handle) {
        return simulator.getNodeRecord(handle);
    }

    public LivenessProvider<Identifier> getLivenessProvider() {
        return livenessProvider;
    }

    public GenericNetworkSimulator<Identifier, MessageType> getGenericSimulator() {
        return simulator;
    }

    public void registerNode(Identifier i, DirectTransportLayer<Identifier, MessageType> dtl, NodeRecord nr) {
        simulator.registerIdentifier(i, dtl, nr);
    }
}
