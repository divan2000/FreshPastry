package org.urajio.freshpastry.org.mpisws.p2p.transport.liveness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.SocketWrapperSocket;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.util.TimerWeakHashMap;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;
import org.urajio.freshpastry.rice.selector.Timer;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.*;

public class LivenessTransportLayerImpl<Identifier> implements
        LivenessTypes,
        LivenessTransportLayer<Identifier, ByteBuffer>,
        TransportLayerCallback<Identifier, ByteBuffer>,
        OverrideLiveness<Identifier> {
    /**
     * Pass the msg to the callback if it is NORMAL
     */
    public static final byte HDR_NORMAL = 0;
    public static final byte HDR_PING = 1;
    public static final byte HDR_PONG = 2;
    private final static Logger logger = LoggerFactory.getLogger(LivenessTransportLayerImpl.class);
    // how long to wait for a ping response to come back before declaring lost
    public final int PING_DELAY;
    // factor of jitter to adjust to the ping waits - we may wait up to this time before giving up
    public final float PING_JITTER;
    // how many tries to ping before giving up
    public final int NUM_PING_TRIES;
    // the initial timeout for exponential backoff
    public final long BACKOFF_INITIAL;
    // the limit on the number of times for exponential backoff
    public final int BACKOFF_LIMIT;
    /**
     * Holds only pending DeadCheckers
     */
    final Map<Identifier, EntityManager> managers;
    // the minimum amount of time between check dead checks on dead routes
    public long CHECK_DEAD_THROTTLE;
    public int DEFAULT_RTO;// = 3000;
    protected TransportLayer<Identifier, ByteBuffer> tl;
    protected Environment environment;
    protected TimeSource time;
    protected Timer timer;
    protected Random random;
    /**
     * RTO helper see RFC 1122 for a detailed description of RTO calculation
     */
    int RTO_UBOUND;// = 10000; // 10 seconds
    /**
     * RTO helper see RFC 1122 for a detailed description of RTO calculation
     */
    int RTO_LBOUND;// = 50;
    /**
     * RTO helper see RFC 1122 for a detailed description of RTO calculation
     */
    double gainH;// = 0.25;
    /**
     * RTO helper see RFC 1122 for a detailed description of RTO calculation
     */
    double gainG;// = 0.125;
    /**
     * TODO: make this a number of failures?  Say 3?  Use 1 if using SourceRoutes
     */
    boolean connectionExceptionMeansFaulty = true;
    boolean destroyed = false;
    List<LivenessListener<Identifier>> livenessListeners;
    List<PingListener<Identifier>> pingListeners;
    private TransportLayerCallback<Identifier, ByteBuffer> callback;
    private ErrorHandler<Identifier> errorHandler;

    public LivenessTransportLayerImpl(TransportLayer<Identifier, ByteBuffer> tl, Environment env, ErrorHandler<Identifier> errorHandler, int checkDeadThrottle) {
        this.tl = tl;
        this.environment = env;
        this.time = env.getTimeSource();
        this.timer = env.getSelectorManager().getTimer();
        random = new Random();
        this.livenessListeners = new ArrayList<>();
        this.pingListeners = new ArrayList<>();
        this.managers = new TimerWeakHashMap<>(env.getSelectorManager(), 300000);

        Parameters p = env.getParameters();
        PING_DELAY = p.getInt("pastry_socket_scm_ping_delay");
        PING_JITTER = p.getFloat("pastry_socket_scm_ping_jitter");
        NUM_PING_TRIES = p.getInt("pastry_socket_scm_num_ping_tries");
        BACKOFF_INITIAL = p.getInt("pastry_socket_scm_backoff_initial");
        BACKOFF_LIMIT = p.getInt("pastry_socket_scm_backoff_limit");
        CHECK_DEAD_THROTTLE = checkDeadThrottle; // 300000
        DEFAULT_RTO = p.getInt("pastry_socket_srm_default_rto"); // 3000 // 3 seconds
        RTO_UBOUND = p.getInt("pastry_socket_srm_rto_ubound");//240000; // 240 seconds
        RTO_LBOUND = p.getInt("pastry_socket_srm_rto_lbound");//1000;
        gainH = p.getDouble("pastry_socket_srm_gain_h");//0.25;
        gainG = p.getDouble("pastry_socket_srm_gain_g");//0.125;

        tl.setCallback(this);
        this.errorHandler = errorHandler;
        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }
    }

    public void clearState(Identifier i) {
        logger.debug("clearState(" + i + ")");
        deleteManager(i);
    }

    public boolean checkLiveness(Identifier i, Map<String, Object> options) {
        return getManager(i).checkLiveness(options);
    }

    public P2PSocket<Identifier> getLSocket(P2PSocket<Identifier> s, EntityManager manager) {
        LSocket sock = new LSocket(manager, s, manager.identifier.get());
        synchronized (manager.sockets) {
            manager.sockets.add(sock);
        }
        return sock;
    }

    public EntityManager getManager(Identifier i) {
        synchronized (managers) {
            EntityManager manager = managers.get(i);
            if (manager == null) {
                manager = new EntityManager(i);
                managers.put(i, manager);
            }
            return manager;
        }
    }

    public EntityManager deleteManager(Identifier i) {
        synchronized (managers) {
            EntityManager manager = managers.remove(i);
            if (manager != null) {
                if (manager.getPending() != null) manager.getPending().cancel();
            }
            return manager;
        }
    }

    public int getLiveness(Identifier i, Map<String, Object> options) {
        logger.debug("getLiveness(" + i + "," + options + ")");
        synchronized (managers) {
            if (managers.containsKey(i))
                return managers.get(i).liveness;
        }
        return LIVENESS_SUSPECTED;
    }

    public void acceptMessages(boolean b) {
        tl.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
        tl.acceptSockets(b);
    }

    public Identifier getLocalIdentifier() {
        return tl.getLocalIdentifier();
    }

    /**
     * Set this to true if you want a ConnectionException to mark the
     * connection as faulty.  Default = true;
     */
    public void connectionExceptionMeansFaulty(boolean b) {
        connectionExceptionMeansFaulty = b;
    }

    public SocketRequestHandle<Identifier> openSocket(final Identifier i, final SocketCallback<Identifier> deliverSocketToMe, final Map<String, Object> options) {
        // this code marks the Identifier faulty if there is an error connecting the socket.  It's possible that this
        // should be moved to the source route manager, but there needs to be a way to cancel the liveness
        // checks, or maybe the higher layer can ignore them.
        logger.info("openSocket(" + i + "," + deliverSocketToMe + "," + options + ")");
        return tl.openSocket(i, new SocketCallback<Identifier>() {
            public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
                deliverSocketToMe.receiveResult(cancellable, getLSocket(sock, getManager(i)));
            }

            public void receiveException(SocketRequestHandle<Identifier> s, Exception ex) {
                // the upper layer is probably going to retry, so mark this dead first
                if (connectionExceptionMeansFaulty) {
                    if (!(ex instanceof java.nio.channels.ClosedChannelException)) {
                        logger.debug("Marking " + s + " dead due to exception opening socket.", ex);
                        getManager(i).markDead(options);
                    }

                }
                deliverSocketToMe.receiveException(s, ex);
            }
        }, options);
    }

    public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(
            final Identifier i,
            final ByteBuffer m,
            final MessageCallback<Identifier, ByteBuffer> deliverAckToMe,
            Map<String, Object> options) {
        final MessageRequestHandleImpl<Identifier, ByteBuffer> handle =
                new MessageRequestHandleImpl<>(i, m, options);

        EntityManager mgr = getManager(i);
        if ((mgr != null) && (mgr.liveness >= LIVENESS_DEAD)) {
            if (deliverAckToMe != null) deliverAckToMe.sendFailed(handle, new NodeIsFaultyException(i, m));
            return handle;
        }

        byte[] msgBytes = new byte[m.remaining() + 1];
        msgBytes[0] = HDR_NORMAL;
        m.get(msgBytes, 1, m.remaining());
        final ByteBuffer myMsg = ByteBuffer.wrap(msgBytes);

        handle.setSubCancellable(tl.sendMessage(i, myMsg, new MessageCallback<Identifier, ByteBuffer>() {
            public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
                if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) {
                    throw new RuntimeException("msg != handle.getSubCancelable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                }
                if (deliverAckToMe != null) deliverAckToMe.ack(handle);
            }

            public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, Exception ex) {
                if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) {
                    throw new RuntimeException("msg != handle.getSubCancelable() (indicates a bug in the code) msg:" + msg + " sub:" + handle.getSubCancellable());
                }
                if (deliverAckToMe == null) {
                    errorHandler.receivedException(i, ex);
                } else {
                    deliverAckToMe.sendFailed(handle, ex);
                }
            }
        }, options));
        return handle;
    }

    public void messageReceived(Identifier i, ByteBuffer m, Map<String, Object> options) throws IOException {
        byte hdr = m.get();
        switch (hdr) {
            case HDR_NORMAL:
                logger.debug("messageReceived(" + i + "," + m + ")");
                callback.messageReceived(i, m, options);
                return;
            case HDR_PING:
                logger.debug("messageReceived(" + i + ", PING)");
                pong(i, m.getLong(), options);      // it's important to the rendezvous layer to reuse the options
                notifyPingListenersPing(i);
                return;
            case HDR_PONG:
                logger.debug("messageReceived(" + i + ", PONG)");
                EntityManager manager = getManager(i);
                long sendTime = m.getLong();
                int rtt = (int) (time.currentTimeMillis() - sendTime);
                if (rtt >= 0) {
                    manager.updateRTO(rtt);
                    boolean markAlive = false;
                    synchronized (manager) {
                        if (manager.getPending() != null) {
                            manager.getPending().pingResponse(sendTime, options);
                            markAlive = true;
                        }
                    }
                    manager.markAlive(options); // do this outside of the synchronized block
                    notifyPingListenersPong(i, rtt, options);
                } else {
                    logger.warn("I think the clock is fishy, rtt must be >= 0, was:" + rtt);
                    errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
                }
                return;
            default:
                errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
        }
    }

    /**
     * True if there was a pending liveness check.
     *
     * @param i
     * @param options
     * @return
     */
    public boolean cancelLivenessCheck(Identifier i, Map<String, Object> options) {
        EntityManager manager = getManager(i);
        if (manager == null) {
            return false;
        }
        return cancelLivenessCheck(manager, options);
    }

    public boolean cancelLivenessCheck(EntityManager manager, Map<String, Object> options) {
        synchronized (manager) {
            if (manager.getPending() != null) {
                manager.getPending().cancel();
                return true;
            }
        }
        manager.markAlive(options);
        return false;
    }

    public String toString() {
        return "LivenessTL{" + getLocalIdentifier() + "}";
    }

    /**
     * Send the ping.
     *
     * @param i
     */
    public boolean ping(final Identifier i, final Map<String, Object> options) {
        logger.debug("ping(" + i + ")");
        if (i.equals(tl.getLocalIdentifier())) return false;
        try {
            SimpleOutputBuffer sob = new SimpleOutputBuffer(1024);
            sob.writeByte(HDR_PING);
            final long now = time.currentTimeMillis();
            new PingMessage(now).serialize(sob);
            tl.sendMessage(i, ByteBuffer.wrap(sob.getBytes()), new MessageCallback<Identifier, ByteBuffer>() {
                public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
                }

                public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, Exception reason) {
                    logger.debug("ping(" + i + "," + now + "," + options + ") failed", reason);
                }
            }, options);
            return true;
        } catch (IOException ioe) {
            //Should not happen.  There must be a bug in our serialization code.
            errorHandler.receivedException(i, ioe);
        }
        return false;
    }

    /**
     * Send the pong();
     *
     * @param i
     * @param senderTime
     */
    public void pong(final Identifier i, final long senderTime, final Map<String, Object> options) {
        logger.debug("pong(" + i + "," + senderTime + ")");
        try {
            SimpleOutputBuffer sob = new SimpleOutputBuffer(1024);
            sob.writeByte(HDR_PONG);
            new PongMessage(senderTime).serialize(sob);
            tl.sendMessage(i, ByteBuffer.wrap(sob.getBytes()), new MessageCallback<Identifier, ByteBuffer>() {
                public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
                }

                public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, Exception reason) {
                    logger.debug("pong(" + i + "," + senderTime + "," + options + ") failed", reason);
                }
            }, options);
        } catch (IOException ioe) {
            //Should not happen.  There must be a bug in our serialization code.
            errorHandler.receivedException(i, ioe);
        }
    }

    public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
        this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<Identifier> handler) {
        errorHandler = handler;
        this.errorHandler = errorHandler;
        if (this.errorHandler == null) {
            this.errorHandler = new DefaultErrorHandler<>();
        }
    }

    public void destroy() {
        logger.info("destroy()");
        destroyed = true;
        tl.destroy();
        livenessListeners.clear();
        livenessListeners = null;
        pingListeners.clear();
        pingListeners = null;
        for (EntityManager em : managers.values()) {
            em.destroy();
        }
        managers.clear();
    }

    public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
        EntityManager m = getManager(s.getIdentifier());
        if (m.liveness > LIVENESS_SUSPECTED) {
            m.updated = 0L;
            m.checkLiveness(s.getOptions());
        }
        callback.incomingSocket(getLSocket(s, getManager(s.getIdentifier())));
    }

    public void addLivenessListener(LivenessListener<Identifier> name) {
        if (destroyed) return;
        synchronized (livenessListeners) {
            livenessListeners.add(name);
        }
    }

    public boolean removeLivenessListener(LivenessListener<Identifier> name) {
        if (destroyed) return true;
        synchronized (livenessListeners) {
            return livenessListeners.remove(name);
        }
    }

    private void notifyLivenessListeners(Identifier i, int liveness, Map<String, Object> options) {
        if (destroyed) return;
        logger.debug("notifyLivenessListeners(" + i + "," + liveness + ")");
        List<LivenessListener<Identifier>> temp;

        synchronized (livenessListeners) {
            temp = new ArrayList<>(livenessListeners);
        }
        for (LivenessListener<Identifier> listener : temp) {
            listener.livenessChanged(i, liveness, options);
        }
    }

    public void addPingListener(PingListener<Identifier> name) {
        synchronized (pingListeners) {
            pingListeners.add(name);
        }
    }

    public boolean removePingListener(PingListener<Identifier> name) {
        synchronized (pingListeners) {
            return pingListeners.remove(name);
        }
    }

    private void notifyPingListenersPing(Identifier i) {
        List<PingListener<Identifier>> temp;
        synchronized (pingListeners) {
            temp = new ArrayList<>(pingListeners);
        }
        for (PingListener<Identifier> listener : temp) {
            listener.pingReceived(i, null);
        }
    }

    private void notifyPingListenersPong(Identifier i, int rtt, Map<String, Object> options) {
        List<PingListener<Identifier>> temp;
        synchronized (pingListeners) {
            temp = new ArrayList<>(pingListeners);
        }
        for (PingListener<Identifier> listener : temp) {
            listener.pingResponse(i, rtt, options);
        }
    }

    public void setLiveness(Identifier i, int liveness, Map<String, Object> options) {
        EntityManager man = getManager(i);
        switch (liveness) {
            case LIVENESS_ALIVE:
                man.markAlive(options);
                return;
            case LIVENESS_SUSPECTED:
                man.markSuspected(options);
                return;
            case LIVENESS_DEAD:
                man.markDead(options);
                return;
            case LIVENESS_DEAD_FOREVER:
                man.markDeadForever(options);
                return;
        }
    }

    /**
     * DESCRIBE THE CLASS
     *
     * @author jeffh
     * @version $Id: SocketCollectionManager.java 3613 2007-02-15 14:45:14Z jstewart $
     */
    protected class DeadChecker extends TimerTask {

        // the path to check
        protected final EntityManager manager;
        // The number of tries that have occurred so far
        protected int tries = 1;
        // the total number of tries before declaring death
        protected int numTries;
        // for debugging
        long startTime; // the start time
        int initialDelay; // the initial expected delay

        Map<String, Object> options;

        public DeadChecker(EntityManager manager, int numTries, int initialDelay, Map<String, Object> options) {

            logger.debug("DeadChecker@" + System.identityHashCode(this) + " CHECKING DEATH OF PATH " + manager.identifier.get() + " rto:" + initialDelay + " options:" + options);


            this.manager = manager;
            this.numTries = numTries;
            this.options = options;

            this.initialDelay = initialDelay;
            this.startTime = time.currentTimeMillis();
        }

        public void pingResponse(long RTT, Map<String, Object> options) {
            if (!cancelled) {
                if (tries > 1) {
                    long delay = time.currentTimeMillis() - startTime;
                    logger.debug("DeadChecker.pingResponse(" + manager.identifier.get() + ") tries=" + tries + " estimated=" + initialDelay + " totalDelay=" + delay);
                }
            }
            logger.debug("Terminated DeadChecker@" + System.identityHashCode(this) + "(" + manager.identifier.get() + ") due to ping.");
            cancel();
        }

        /**
         * Main processing method for the DeadChecker object
         * <p>
         * value of tries before run() is called:the time since ping was called:the time since deadchecker was started
         * <p>
         * 1:500:500
         * 2:1000:1500
         * 3:2000:3500
         * 4:4000:7500
         * 5:8000:15500 // ~15 seconds to find 1 path faulty, using source routes gives us 30 seconds to find a node faulty
         */
        public void run() {
            if (destroyed) return;
            if (tries < numTries) {
                tries++;
                manager.markSuspected(options);

                Identifier temp = manager.identifier.get();
                if (temp != null) { // could happen during a garbage collection
                    logger.debug("DeadChecker@" + System.identityHashCode(this) + "(" + temp + ") pinging " + tries + " " + manager.getPending());
                    ping(temp, options);
                }
                int absPD = (int) (PING_DELAY * Math.pow(2, tries - 1));
                int jitterAmt = (int) (((float) absPD) * PING_JITTER);
                int scheduledTime = absPD - jitterAmt + random.nextInt(jitterAmt * 2);
                timer.schedule(this, scheduledTime);
            } else {
                logger.debug("DeadChecker@" + System.identityHashCode(this) + "(" + manager.identifier.get() + ") expired - marking as dead.");
                manager.markDead(options);
            }
        }

        public boolean cancel() {
            synchronized (manager) {
                manager.setPending(null);
            }
            return super.cancel();
        }

        public String toString() {
            return "DeadChecker(" + manager.identifier.get() + " #" + System.identityHashCode(this) + "):" + tries + "/" + numTries;
        }
    }

    /**
     * Internal class which is charges with managing the remote connection via
     * a specific route
     */
    public class EntityManager {

        protected final Set<LSocket> sockets;
        // the remote route of this manager
        protected WeakReference<Identifier> identifier;
        // the current liveness of this route
        protected int liveness;
        // the last time the liveness information was updated
        protected long updated;
        /**
         * Retransmission Time Out
         */
        int RTO = DEFAULT_RTO;
        /**
         * Average RTT
         */
        double RTT = 0;
        /**
         * Standard deviation RTT
         */
        double standardD = RTO / 4.0;  // RFC1122 recommends choose value to target RTO = 3 seconds
        // whether or not a check dead is currently being carried out on this route
        private DeadChecker pendingDeadchecker;

        public EntityManager(Identifier identifier) {
            if (identifier == null) throw new IllegalArgumentException("identifier is null");
            this.identifier = new WeakReference<>(identifier);
            this.liveness = LIVENESS_SUSPECTED;

            this.pendingDeadchecker = null;
            this.updated = 0L;
            sockets = new HashSet<>();
        }

        public DeadChecker getPending() {
            return pendingDeadchecker;
        }

        public void setPending(DeadChecker d) {
            pendingDeadchecker = d;
        }

        public void removeSocket(LSocket socket) {
            synchronized (sockets) {
                sockets.remove(socket);
            }
        }

        public int rto() {
            return RTO;
        }

        /**
         * This method should be called when this route is declared
         * alive.
         */
        protected void markAlive(Map<String, Object> options) {
            boolean notify = false;
            if (liveness != LIVENESS_ALIVE) notify = true;
            this.liveness = LIVENESS_ALIVE;
            if (notify) {
                Identifier temp = identifier.get();
                if (temp != null) {
                    notifyLivenessListeners(temp, liveness, options);
                }
            }
        }

        /**
         * This method should be called when this route is declared
         * suspected.
         */
        protected void markSuspected(Map<String, Object> options) {
            // note, can only go from alive -> suspected, can't go from dead->suspected
            if (liveness > LIVENESS_SUSPECTED) return;

            boolean notify = false;
            if (liveness != LIVENESS_SUSPECTED) notify = true;
            this.liveness = LIVENESS_SUSPECTED;
            if (notify) {
                logger.debug(this + ".markSuspected() notify = true");
                Identifier temp = identifier.get();
                if (temp != null) {
                    notifyLivenessListeners(temp, liveness, options);
                }
            }
        }

        /**
         * This method should be called when this route is declared
         * dead.
         */
        protected void markDead(Map<String, Object> options) {
            boolean notify = false;
            if (liveness < LIVENESS_DEAD) notify = true;

            logger.debug(this + ".markDead() notify:" + notify);
            markDeadHelper(LIVENESS_DEAD, options, notify);
        }

        protected void markDeadForever(Map<String, Object> options) {
            boolean notify = false;
            if (liveness < LIVENESS_DEAD_FOREVER) notify = true;
            logger.debug(this + ".markDeadForever() notify:" + notify);
            markDeadHelper(LIVENESS_DEAD_FOREVER, options, notify);
        }

        protected void markDeadHelper(int liveness, Map<String, Object> options, boolean notify) {
            this.liveness = liveness;
            if (getPending() != null) {
                getPending().cancel(); // sets to null too
            }

            // it's very important to notify before closing the sockets,
            // otherwise the higher layers may fight you here by trying to open
            // sockets that this layer is closing
            if (notify) {
                Identifier temp = identifier.get();
                if (temp != null) {
                    notifyLivenessListeners(temp, liveness, options);
                } else {
                    logger.warn("markDeadHelper(" + liveness + "," + options + "," + notify + ") temp == null!  Can't notify listeners!");
                }
            }

            ArrayList<LSocket> temp;
            synchronized (sockets) {
                // the close() operation can cause a ConcurrentModificationException
                temp = new ArrayList<>(sockets);
                sockets.clear();
            }
            for (LSocket sock : temp) {
                logger.info("closing " + sock);
                sock.close();
            }
        }

        /**
         * Adds a new round trip time datapoint to our RTT estimate, and
         * updates RTO and standardD accordingly.
         *
         * @param m new RTT
         */
        private void updateRTO(long m) {
            if (m < 0) throw new IllegalArgumentException("rtt must be >= 0, was:" + m);

            // rfc 1122
            double err = m - RTT;
            double absErr = err;
            if (absErr < 0) {
                absErr *= -1;
            }
            RTT = RTT + gainG * err;
            standardD = standardD + gainH * (absErr - standardD);
            RTO = (int) (RTT + (4.0 * standardD));
            if (RTO > RTO_UBOUND) {
                RTO = RTO_UBOUND;
            }
            if (RTO < RTO_LBOUND) {
                RTO = RTO_LBOUND;
            }
        }

        /**
         * Method which checks to see this route is dead.  If this address has
         * been checked within the past CHECK_DEAD_THROTTLE millis, then
         * this method does not actually do a check.
         *
         * @return true if there will be an update (either a ping, or a change in liveness)
         */
        protected boolean checkLiveness(final Map<String, Object> options) {
            logger.debug(this + ".checkLiveness()");

            boolean ret = false;
            int rto = DEFAULT_RTO;
            synchronized (this) {
                if (this.getPending() != null) {
                    // prolly won't change
                    return this.liveness < LIVENESS_DEAD;
                }

                long now = time.currentTimeMillis();
                if ((this.liveness < LIVENESS_DEAD) ||
                        (this.updated < now - CHECK_DEAD_THROTTLE)) {
                    this.updated = now;
                    rto = rto();
                    this.setPending(new DeadChecker(this, NUM_PING_TRIES, rto, options));
                    ret = true;
                } else {
                    logger.debug(this + ".checkLiveness() not checking " + identifier.get() + " checked to recently, can't check for " + ((updated + CHECK_DEAD_THROTTLE) - now) + " millis.");
                }
            }
            if (ret) {
                final int theRTO = rto;

                Runnable r = new Runnable() {
                    public void run() {
                        if (getPending() == null) return;  // could have been set to null in the meantime
                        timer.schedule(getPending(), theRTO);
                        Identifier temp = identifier.get();
                        if (temp != null) {
                            ping(temp, options);
                        }
                    }

                    public String toString() {
                        return EntityManager.this.toString();
                    }
                };
                if (environment.getSelectorManager().isSelectorThread()) {
                    r.run();
                } else {
                    environment.getSelectorManager().invoke(r);
                }
            }

            if (this.liveness >= LIVENESS_DEAD) return false; // prolly won't change

            return ret;
        }

        public String toString() {
            Identifier temp = identifier.get();
            if (temp == null) return "null";
            return temp.toString();
        }

        public void destroy() {
            if (getPending() != null) getPending().cancel();
        }
    }

    /**
     * The purpose of this class is to checkliveness on a stalled socket that we are waiting to write on.
     * <p>
     * the livenessCheckerTimer is set every time we want to write, and killed every time we do write
     * <p>
     * TODO: think about exactly what we want to use for the delay on the timer, currently using rto*4
     *
     * @author Jeff Hoye
     */
    class LSocket extends SocketWrapperSocket<Identifier, Identifier> {
        EntityManager manager;

        /**
         * This is for memory management, so that we don't collect the identifier in the EntityManager
         * while we still have open sockets.
         */
        Identifier hardRef;

        /**
         * set every time we want to write, and killed every time we do write
         */
        TimerTask livenessCheckerTimer;

        boolean closed = false;

        public LSocket(EntityManager manager, P2PSocket<Identifier> socket, Identifier hardRef) {
            super(socket.getIdentifier(), socket, LivenessTransportLayerImpl.this.errorHandler, socket.getOptions());
            if (hardRef == null) {
                throw new IllegalArgumentException("hardRef == null " + manager + " " + socket);
            }
            this.manager = manager;
            this.hardRef = hardRef;
        }

        @Override
        public void register(boolean wantToRead, boolean wantToWrite, final P2PSocketReceiver<Identifier> receiver) {
            if (closed) {
                receiver.receiveException(this, new ClosedChannelException("Socket " + this + " is already closed."));
                return;
            }
            if (wantToWrite) startLivenessCheckerTimer();
            super.register(wantToRead, wantToWrite, receiver);
        }

        @Override
        public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
            EntityManager m = getManager(socket.getIdentifier());
            if (m.liveness > LIVENESS_SUSPECTED) {
                m.updated = 0L;
                m.checkLiveness(socket.getOptions());
            }
            if (canWrite) {
                stopLivenessCheckerTimer();
            }
            super.receiveSelectResult(socket, canRead, canWrite);
        }

        public void startLivenessCheckerTimer() {
            synchronized (LSocket.this) {
                if (livenessCheckerTimer != null) return;
                livenessCheckerTimer = new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (LSocket.this) {
                            if (livenessCheckerTimer == this) livenessCheckerTimer = null;
                        }
                        manager.checkLiveness(options);
                    }
                };
            } // sync
            logger.debug("Checking liveness on " + manager.identifier.get() + " in " + manager.rto() + " millis if we don't write.");
            timer.schedule(livenessCheckerTimer, manager.rto() * 4, 30000);
        }

        public void stopLivenessCheckerTimer() {
            synchronized (LSocket.this) {
                if (livenessCheckerTimer != null) livenessCheckerTimer.cancel();
                livenessCheckerTimer = null;
            }
        }

        //    Exception closeEx;
        @Override
        public void close() {
            closed = true;
            manager.removeSocket(this);
            super.close();
        }

        public String toString() {
            return "LSocket{" + socket + "}";
        }
    }
}
