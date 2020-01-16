package org.urajio.freshpastry.org.mpisws.p2p.transport.proximity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.PingListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.Pinger;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.time.TimeSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MinRTTProximityProvider<Identifier> implements ProximityProvider<Identifier>, PingListener<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(MinRTTProximityProvider.class);
    /**
     * Holds only pending DeadCheckers
     */
    final Map<Identifier, EntityManager> managers;
    final Collection<ProximityListener<Identifier>> listeners = new ArrayList<>();
    /**
     * millis for the timeout
     * <p>
     * The idea is that we don't want this parameter to change too fast,
     * so this is the timeout for it to increase, you could set this to infinity,
     * but that may be bad because it doesn't account for intermediate link failures
     */
    public int PROX_TIMEOUT;// = 60*60*1000;
    Pinger<Identifier> tl;
    TimeSource time;
    int pingThrottle = 5000; // TODO: Make configurable

    public MinRTTProximityProvider(Pinger<Identifier> tl, Environment env) {
        this.tl = tl;
        this.time = env.getTimeSource();
        tl.addPingListener(this);
        this.managers = new HashMap<>();
    }

    public int proximity(Identifier i, Map<String, Object> options) {
        EntityManager manager = getManager(i);
        int ret = manager.proximity;
        if (ret == DEFAULT_PROXIMITY) {
            manager.ping(options);
        }
        return ret;
    }

    public void pingResponse(Identifier i, int rtt, Map<String, Object> options) {
        getManager(i).markProximity(rtt, options);
    }

    public void pingReceived(Identifier i, Map<String, Object> options) {

    }

    public void clearState(Identifier i) {
        synchronized (managers) {
            managers.remove(i);
        }
    }

    public EntityManager getManager(Identifier i) {
        synchronized (managers) {
            EntityManager manager = managers.get(i);
            if (manager == null) {
                manager = new EntityManager(i);
                logger.debug("Creating EM for " + i);
                managers.put(i, manager);
            }
            return manager;
        }
    }

    public void addProximityListener(ProximityListener<Identifier> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public boolean removeProximityListener(ProximityListener<Identifier> listener) {
        synchronized (listeners) {
            return listeners.remove(listener);
        }
    }

    public void notifyProximityListeners(Identifier i, int prox, Map<String, Object> options) {
        Collection<ProximityListener<Identifier>> temp;
        synchronized (listeners) {
            temp = new ArrayList<>(listeners);
        }
        for (ProximityListener<Identifier> p : temp) {
            p.proximityChanged(i, prox, options);
        }
    }

    /**
     * Internal class which is charges with managing the remote connection via
     * a specific route
     */
    public class EntityManager {

        // the remote route of this manager
        protected Identifier identifier;

        // the current best-known proximity of this route
        protected int proximity;

        protected long lastPingTime = Integer.MIN_VALUE; // we don't want underflow, but we don't want this to be zero either

        /**
         * Constructor - builds a route manager given the route
         *
         * @param route The route
         */
        public EntityManager(Identifier route) {
            if (route == null) throw new IllegalArgumentException("route is null");
            this.identifier = route;
            proximity = DEFAULT_PROXIMITY;
        }

        public void ping(Map<String, Object> options) {
            long now = time.currentTimeMillis();
            if ((now - lastPingTime) < pingThrottle) {
                logger.debug("Dropping ping because pingThrottle." + (pingThrottle - (now - lastPingTime)));
                return;
            }
            lastPingTime = now;
            tl.ping(identifier, options);
        }

        /**
         * Method which returns the last cached proximity value for the given address.
         * If there is no cached value, then DEFAULT_PROXIMITY is returned.
         */
        public int proximity() {
            return proximity;
        }

        /**
         * This method should be called when this route has its proximity updated
         *
         * @param proximity The proximity
         */
        protected void markProximity(int proximity, Map<String, Object> options) {
            if (proximity < 0) throw new IllegalArgumentException("proximity must be >= 0, was:" + proximity);
            logger.debug(this + ".markProximity(" + proximity + ")");
            if (this.proximity > proximity) {
                logger.debug(this + " updating proximity to " + proximity);
                this.proximity = proximity;
                notifyProximityListeners(identifier, proximity, options);
            }
        }

        public String toString() {
            return identifier.toString();
        }
    }
}

