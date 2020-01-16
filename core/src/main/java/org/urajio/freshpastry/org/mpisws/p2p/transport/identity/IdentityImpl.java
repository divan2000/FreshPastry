package org.urajio.freshpastry.org.mpisws.p2p.transport.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.LivenessTypes;
import org.urajio.freshpastry.org.mpisws.p2p.transport.liveness.OverrideLiveness;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityListener;
import org.urajio.freshpastry.org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.urajio.freshpastry.org.mpisws.p2p.transport.util.*;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleInputBuffer;
import org.urajio.freshpastry.rice.p2p.util.rawserialization.SimpleOutputBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class IdentityImpl<UpperIdentifier, MiddleIdentifier, UpperMsgType, LowerIdentifier> implements LivenessTypes {
    public static final byte SUCCESS = 1;
    public static final byte FAILURE = 0;
    public static final byte NO_ID = 2;
    public static final byte NORMAL = 1;
    public static final byte INCORRECT_IDENTITY = 0;
    //  public static final String NODE_HANDLE_FROM_INDEX = "identity.node_handle_to_index";
    public static final String NODE_HANDLE_FROM_INDEX = "identity.node_handle_to_index";
    //  public static final String NODE_HANDLE_FROM_INDEX = "identity.node_handle_from_index";
    public static final String DONT_VERIFY = "identity.dont_verify_dest";
    private final static Logger logger = LoggerFactory.getLogger(IdentityImpl.class);
    protected final Map<UpperIdentifier, Set<IdentityMessageHandle>> pendingMessages;
    public BindStrategy<UpperIdentifier, LowerIdentifier> bindStrategy;
    protected byte[] localIdentifier;
    protected LowerIdentityImpl lower;
    protected UpperIdentityImpl upper;
    protected Set<UpperIdentifier> deadForever;  // TODO: make TimerWeakHashSet
    protected Environment environment;
    protected IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> serializer;
    protected NodeChangeStrategy<UpperIdentifier> nodeChangeStrategy;
    protected SanityChecker<UpperIdentifier, MiddleIdentifier> sanityChecker;
    /**
     * Multiple UpperIdentifiers claim the same binding to the
     * LowerIdentifier.
     * <p>
     * If these are unrelated nodes (different ID/credentials) we need to
     * make sure to expire the node first.  If they are the same node, and
     * just a new epoch, then we can expire them right away.
     * <p>
     * Note, that it's possible to have the UpperIdentifier in multiple places if it
     * has multiple paths (such as source routing)
     */
    protected Map<MiddleIdentifier, UpperIdentifier> bindings;  // this one may be difficult as the Upper has a ref to the lower
    /**
     * Held in the options map of the message/socket.  This is a pointer from the upper
     * level to the lower level.
     */
    int intendedDestCtr = Integer.MIN_VALUE;
    OverrideLiveness<LowerIdentifier> overrideLiveness;

    public IdentityImpl(
            byte[] localIdentifier,
            IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> serializer,
            NodeChangeStrategy<UpperIdentifier> nodeChangeStrategy,
            SanityChecker<UpperIdentifier, MiddleIdentifier> sanityChecker,
            BindStrategy<UpperIdentifier, LowerIdentifier> bindStrategy,
            Environment environment) {
        this.bindStrategy = bindStrategy;
        this.sanityChecker = sanityChecker;
        if (sanityChecker == null) throw new IllegalArgumentException("SanityChecker is null");
        this.localIdentifier = localIdentifier;
        this.serializer = serializer;
        this.nodeChangeStrategy = nodeChangeStrategy;
        this.environment = environment;

        this.pendingMessages = new HashMap<>();
        this.deadForever = Collections.synchronizedSet(new HashSet<>());

//    this.intendedDest = new HashMap<Integer, WeakReference<UpperIdentifier>>(); // this is a memory leak, but very slow, maybe we should make a periodic iterator to clean this out...
//    this.reverseIntendedDest = new TimerWeakHashMap<UpperIdentifier, Integer>(environment.getSelectorManager(), 300000);

        this.bindings = new HashMap<>();

        serializer.addSerializerListener(new SerializerListener<UpperIdentifier>() {
            public void nodeHandleFound(final UpperIdentifier handle) {
                Runnable r = new Runnable() {
                    public void run() {
                        addBinding(handle, null, null); // I hope setting the options to null is ok...
                    }
                };
                if (IdentityImpl.this.environment.getSelectorManager().isSelectorThread()) {
                    r.run();
                } else {
                    IdentityImpl.this.environment.getSelectorManager().invoke(r);
                }
            }
        });
    }

    public void addPendingMessage(UpperIdentifier i, IdentityMessageHandle ret) {
        logger.debug("addPendingMessage(" + i + "," + ret + ")");
        synchronized (pendingMessages) {
            Set<IdentityMessageHandle> set = pendingMessages.get(i);
            if (set == null) {
                set = new HashSet<>();
                pendingMessages.put(i, set);
            }
            set.add(ret);
        }
    }

    public void removePendingMessage(UpperIdentifier i, IdentityMessageHandle ret) {
        synchronized (pendingMessages) {
            Set<IdentityMessageHandle> set = pendingMessages.get(i);
            if (set == null) {
                return;
            }
            set.remove(ret);
            if (set.isEmpty()) {
                pendingMessages.remove(i);
            }
        }
    }

    public void printMemStats(int logLevel) {
        if (logger.isDebugEnabled()) {
            synchronized (pendingMessages) {
                int queueSum = 0;
                for (UpperIdentifier i : pendingMessages.keySet()) {
                    Set<IdentityMessageHandle> theSet = pendingMessages.get(i);
                    int queueSize = 0;
                    if (theSet != null) {
                        queueSize = theSet.size();
                    }
                    queueSum += queueSize;
                    logger.debug("PM{" + i + "," + upper.getLiveness(i, null) + "} queue:" + queueSize);
                }
                logger.debug("NumUpperIds:" + pendingMessages.size() + " numPendingMsgs:" + queueSum);
            } // synchronized
        }
    }

    public void setOverrideLiveness(OverrideLiveness<LowerIdentifier> ol) {
        this.overrideLiveness = ol;
    }

    /**
     * Put this in lower.
     *
     * @param l
     * @param i
     * @param options
     */
    public void setDeadForever(LowerIdentifier l, UpperIdentifier i, Map<String, Object> options) {
        if (deadForever.contains(i)) return;
        logger.debug("setDeadForever(" + l + "," + i + "," + options + ")", new Exception("Stack Trace"));
        deadForever.add(i);
//    try {
//      logger.log("setDeadForever("+l+","+i+","+(options == null)+"):overL:"+overrideLiveness);
        Map<String, Object> o2 = OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, i);
        // how do we notify?  If we have a lower, then let it trickle through the TLs, otherwise, call it only on the upper
        if (l == null) {
            logger.info("setDeadForever(" + l + "," + i + "," + options + "): l == null");
            upper.setLiveness(i, LIVENESS_DEAD_FOREVER, o2);
        } else {
            overrideLiveness.setLiveness(l, LIVENESS_DEAD_FOREVER, o2);
        }
//    } catch (NullPointerException npe) {
//      logger.log("setDeadForever("+l+","+i+","+options+"):overL:"+overrideLiveness);
//      throw npe;
//    }
//    upper.notifyLivenessListeners(i, LIVENESS_DEAD_FOREVER, options);  // now called as a result of overrideLiveness
        Set<IdentityMessageHandle> cancelMe = pendingMessages.remove(i);
        if (cancelMe != null) {
            for (IdentityMessageHandle msg : cancelMe) {
                msg.deadForever();
            }
        }
        upper.clearState(i);
    }

    protected UpperIdentifier getIntendedDest(Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("options is null");
        }
        if (!options.containsKey(NODE_HANDLE_FROM_INDEX))
            throw new IllegalArgumentException("options doesn't have NODE_HANDLE_FROM_INDEX " + options);

        return (UpperIdentifier) options.get(NODE_HANDLE_FROM_INDEX);
    }

    /**
     * @param u
     * @param l       is optional, and is set when the lower-layer calls it
     * @param options
     * @return false if the new binding is actually old (IE, don't upgrade it)
     */
    protected boolean addBinding(UpperIdentifier u, LowerIdentifier l, Map<String, Object> options) {
        if (!environment.getSelectorManager().isSelectorThread())
            throw new RuntimeException("Must be called on selector thread.");
        if (bindStrategy != null && options != null) {
            if (!bindStrategy.accept(u, l, options)) return false;
        }
        MiddleIdentifier m = serializer.translateDown(u);
//    synchronized(bindings) { // synchronization by selector thread
        if (deadForever.contains(u)) return false;

        UpperIdentifier old = bindings.get(m);
        if (old == null) {
            logger.debug("addBinding(" + u + "," + l + ") old is null");
            bindings.put(m, u);
            if (l != null) {
                overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
            } else {
                upper.setLiveness(u, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
            }
            return true;
        } else {
            if (old.equals(u)) {
                logger.debug("addBinding(" + u + "," + l + ") old is equal");
                if (l != null) {
                    overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
                } else {
                    upper.setLiveness(u, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
                }
                return true;
            }

            // they are different
            if (destinationChanged(old, u, l, options)) {
                bindings.put(m, u);
                logger.debug("addBinding(" + u + "," + l + ") old " + old + " is dead");
                if (l == null) {
                    logger.info("addBinding(" + u + "," + l + "): l == null");
                    upper.setLiveness(u, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
                } else {
                    overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
                }
                return true;
            } else {
                // mark the new one as faulty
                logger.info("The nodeChangeStrategy found identifier " + u +
                        " to be stale.  Should be using " + old);
//          setDeadForever(l, u, options);  
                return false;
            }
        }
//    }
    }

    /**
     * Put this in lower.
     * <p>
     * Return true if the new one is a valid replacement of the old
     * <p>
     * True if they are the same
     *
     * @param oldDest
     * @param newDest
     * @param i
     * @return
     */
    public boolean destinationChanged(UpperIdentifier oldDest, UpperIdentifier newDest, LowerIdentifier i, Map<String, Object> options) {
        if (oldDest.equals(newDest)) {
//            if (logger.level <= Logger.FINE) logger.log("1");
            // don't do anything
            return true;
        } else {
//            if (logger.level <= Logger.FINE) logger.log("2");
            if (deadForever.contains(oldDest)) {
                return true;
            }
            if (deadForever.contains(newDest)) {
                return false;
            }

//              if (logger.level <= Logger.FINE) logger.log("3");              
//              if (logger.level <= Logger.FINE) logger.log("4");
            if (nodeChangeStrategy.canChange(oldDest, newDest)) {
//                if (logger.level <= Logger.FINE) logger.log("5");
                logger.info("destinationChanged(" + oldDest + "->" + newDest + "," + i + "," + options + ")");
                setDeadForever(i, oldDest, options);
                return true;
            } else {
                logger.info("destinationDidntChange(" + newDest + "->" + oldDest + "," + i + "," + options + ")");
                setDeadForever(i, newDest, options);
                return false;
            }
        }
    }

    public void initLowerLayer(TransportLayer<LowerIdentifier, ByteBuffer> tl, ErrorHandler<LowerIdentifier> handler) {
        lower = new LowerIdentityImpl(tl, handler);
    }

    public LowerIdentity<LowerIdentifier, ByteBuffer> getLowerIdentity() {
        return lower;
    }

    public UpperIdentity<UpperIdentifier, UpperMsgType> getUpperIdentity() {
        return upper;
    }

    public void initUpperLayer(UpperIdentifier localIdentifier, TransportLayer<MiddleIdentifier, UpperMsgType> tl,
                               LivenessProvider<MiddleIdentifier> live,
                               ProximityProvider<MiddleIdentifier> prox,
                               OverrideLiveness<LowerIdentifier> overrideLiveness) {
        if (upper != null) throw new IllegalStateException("upper already initialized:" + upper);
        upper = new UpperIdentityImpl(localIdentifier, tl, live, prox);

        setOverrideLiveness(overrideLiveness);
    }

    class LowerIdentityImpl implements LowerIdentity<LowerIdentifier, ByteBuffer>, TransportLayerCallback<LowerIdentifier, ByteBuffer> {
        private final Logger logger = LoggerFactory.getLogger(LowerIdentityImpl.class);

        TransportLayer<LowerIdentifier, ByteBuffer> tl;
        TransportLayerCallback<LowerIdentifier, ByteBuffer> callback;
        ErrorHandler<LowerIdentifier> errorHandler;

        public LowerIdentityImpl(
                TransportLayer<LowerIdentifier, ByteBuffer> tl,
                ErrorHandler<LowerIdentifier> handler) {
            this.tl = tl;
            if (handler != null) {
                this.errorHandler = handler;
            } else {
                this.errorHandler = new DefaultErrorHandler<>();
            }

            tl.setCallback(this);
        }

        public SocketRequestHandle<LowerIdentifier> openSocket(
                final LowerIdentifier i,
                final SocketCallback<LowerIdentifier> deliverSocketToMe,
                final Map<String, Object> options) {
            logger.info("openSocket(" + i + "," + deliverSocketToMe + "," + options + ")");

            // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?
            // May need to re-think this SRHI at all the layers
            final SocketRequestHandleImpl<LowerIdentifier> ret = new SocketRequestHandleImpl<>(i, options);
            SimpleOutputBuffer sob = new SimpleOutputBuffer();
            try {
                if ((options == null) || options.containsKey(DONT_VERIFY) && (Boolean) options.get(DONT_VERIFY)) {
                    // don't send TO
                    sob.writeByte(0);
                } else {
                    // send TO
                    sob.writeByte(1);
                    final UpperIdentifier dest = getIntendedDest(options);

                    if (dest == null) {
                        deliverSocketToMe.receiveException(ret, new MemoryExpiredException("No record of the upper identifier for " + i + " index=" + options.get(NODE_HANDLE_FROM_INDEX)));
                        return ret;
                    }

                    if (addBinding(dest, i, options)) {
                        // no problem, sending message
                    } else {
                        deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
                        return ret;
                    }

                    serializer.serialize(sob, dest);
                }

                // write FROM
                sob.write(localIdentifier);
//        logger.log("writing:"+Arrays.toString(sob.getBytes()));
            } catch (IOException ioe) {
                deliverSocketToMe.receiveException(ret, ioe);
                return ret;
            }
            final ByteBuffer buf;
            buf = sob.getByteBuffer();
            ret.setSubCancellable(tl.openSocket(i,
                    new SocketCallback<LowerIdentifier>() {

                        public void receiveResult(SocketRequestHandle<LowerIdentifier> cancellable, final P2PSocket<LowerIdentifier> sock) {
                            ret.setSubCancellable(new Cancellable() {
                                public boolean cancel() {
                                    sock.close();
                                    return true;
                                }
                            });
                            try {
                                new P2PSocketReceiver<LowerIdentifier>() {

                                    public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                                        if (canRead) throw new IOException("Never asked to read!");
                                        if (!canWrite) throw new IOException("Can't write!");
                                        if (socket.write(buf) < 0) {
                                            deliverSocketToMe.receiveException(ret, new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                                            return;
                                        }
                                        if (buf.hasRemaining()) {
                                            socket.register(false, true, this);
                                        } else {
                                            // wait for the response
                                            socket.register(true, false, new P2PSocketReceiver<LowerIdentifier>() {
                                                ByteBuffer responseBuffer = ByteBuffer.allocate(1);

                                                public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                                                    deliverSocketToMe.receiveException(ret, ioe);
                                                }

                                                public void receiveSelectResult(final P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                                                    if (!canRead) throw new IOException("Can't read!");
                                                    if (canWrite) throw new IOException("Never asked to write!");
                                                    if (socket.read(responseBuffer) == -1) {
                                                        // socket unexpectedly closed
                                                        socket.close();
                                                        deliverSocketToMe.receiveException(ret, new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                                                        return;
                                                    }

                                                    if (responseBuffer.remaining() > 0) {
                                                        socket.register(true, false, this);
                                                    } else {
                                                        byte answer = responseBuffer.array()[0];
                                                        if (answer == FAILURE) {
                                                            // wrong address, read more
                                                            logger.info("openSocket(" + i + "," + deliverSocketToMe + ") answer = FAILURE");
                                                            //                            setDeadForever(dest);
                                                            UpperIdentifier newDest = serializer.deserialize(new SocketInputBuffer(socket, localIdentifier.length), i);

                                                            if (addBinding(newDest, i, options)) {
                                                                //                              overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, u));
                                                            } else {
                                                                // This is really bad if we get here, because someone responded to us who is dead, maybe a big network lag could cause this?
                                                                socket.close();
                                                                deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
                                                            }

                                                            // need to do this so the boostrapper knows the proper identity
                                                            // done in addBinding now
                                                            //                            upper.setLiveness(newDest, LIVENESS_ALIVE, options);
                                                            deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
                                                        } else {
                                                            ret.setSubCancellable(new Cancellable() {
                                                                public boolean cancel() {
                                                                    throw new IllegalStateException("Can't cancel, already delivered. ret:" + ret + " sock:" + socket);
                                                                    //                                return false;
                                                                }
                                                            }); // can't cancel any more
                                                            deliverSocketToMe.receiveResult(ret, socket);
                                                        }
                                                    }
                                                }
                                            });
                                        }
                                    }

                                    public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                                        deliverSocketToMe.receiveException(ret, ioe);
                                    }
                                }.receiveSelectResult(sock, false, true);
                            } catch (IOException ioe) {
                                deliverSocketToMe.receiveException(ret, ioe);
                            }
                        }

                        public void receiveException(SocketRequestHandle<LowerIdentifier> s, Exception ex) {
                            deliverSocketToMe.receiveException(ret, ex);
                        }

                    },
                    options));
            return ret;
        }

        public void incomingSocket(P2PSocket<LowerIdentifier> s) throws IOException {
            logger.debug("incomingSocket(" + s + ")");

            // see if it wants to verify us
            new P2PSocketReceiver<LowerIdentifier>() {
                ByteBuffer buf = ByteBuffer.allocate(1);

                public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                    errorHandler.receivedException(socket.getIdentifier(), ioe);
                }

                public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                    if (socket.read(buf) < 0) {
                        // socket closed
                        if (logger.isInfoEnabled())
                            errorHandler.receivedException(socket.getIdentifier(), new IOException("Socket closed while incoming."));
                        return;
                    }
                    if (buf.hasRemaining()) {
                        // need to read more
                        socket.register(true, false, this);
                        return;
                    }

                    buf.clear();
                    byte wantsToVerifyB = buf.get();
                    final boolean wantsToVerify = (wantsToVerifyB == (byte) 1);

                    new P2PSocketReceiver<LowerIdentifier>() {
                        ByteBuffer buf = ByteBuffer.allocate(localIdentifier.length);

                        public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                            errorHandler.receivedException(socket.getIdentifier(), ioe);
                        }

                        public void receiveSelectResult(final P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                            if (canWrite) throw new IOException("Never asked to write!");
                            if (!canRead) throw new IOException("Can't read!");
                            if (wantsToVerify) {
                                // the node is sending who he thinks he is talking to

                                // read the TO field
                                if (socket.read(buf) < 0) {
                                    // socket closed
                                    if (logger.isInfoEnabled())
                                        errorHandler.receivedException(socket.getIdentifier(), new IOException("Socket closed while incoming."));
                                    return;
                                }

                                if (buf.hasRemaining()) {
                                    // need to read more
                                    socket.register(true, false, this);
                                    return;
                                }

                                if (!Arrays.equals(buf.array(), localIdentifier)) {
                                    logger.info("incomingSocket() FAILURE expected " +
                                            Arrays.toString(buf.array()) + " me:" +
                                            Arrays.toString(localIdentifier));

                                    // not expecting me, send failure
                                    byte[] result = new byte[1 + localIdentifier.length];
                                    result[0] = FAILURE;
                                    System.arraycopy(localIdentifier, 0, result, 1, localIdentifier.length);
                                    final ByteBuffer writeMe = ByteBuffer.wrap(result);
                                    new P2PSocketReceiver<LowerIdentifier>() {
                                        public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                                            errorHandler.receivedException(socket.getIdentifier(), ioe);
                                        }

                                        public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                                            if (canRead) throw new IOException("Not expecting to read.");
                                            if (!canWrite) throw new IOException("Expecting to write.");

                                            if (socket.write(writeMe) == -1) {
                                                // socket closed
                                                if (logger.isInfoEnabled())
                                                    errorHandler.receivedException(socket.getIdentifier(), new ClosedChannelException("Error on incoming socket."));
                                                return;
                                            }

                                            if (buf.hasRemaining()) {
                                                // need to read more
                                                socket.register(false, true, this);
                                                return;
                                            }
                                        }
                                    }.receiveSelectResult(socket, false, true); // write failure
                                    return;
                                }
                            } else {
                                // doesn't want to verify
                                logger.info("Connection from " + socket.getIdentifier() + " didn't want to verify our identity.");
                            }
                            // either he doesn't want to verify, or the TO was me,
                            // now read the FROM, and add the proper index into the options
                            final SocketInputBuffer sib = new SocketInputBuffer(socket, 1024);
                            new P2PSocketReceiver<LowerIdentifier>() {
                                public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                                    errorHandler.receivedException(socket.getIdentifier(), ioe);
                                }

                                public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                                    if (canWrite) throw new IOException("Never asked to write!");
                                    if (!canRead) throw new IOException("Can't read!");

                                    final Map<String, Object> newOptions = OptionsFactory.copyOptions(socket.getOptions());

                                    UpperIdentifier from;
                                    try {
                                        // add to intendedDest, add option index
                                        from = serializer.deserialize(sib, socket.getIdentifier());
                                        newOptions.put(NODE_HANDLE_FROM_INDEX, from);
                                    } catch (InsufficientBytesException ibe) {
                                        socket.register(true, false, this);
                                        return;
                                    }

                                    // once we are here, we have succeeded in deserializing the NodeHanlde, and added it to the new options
                                    if (addBinding(from, socket.getIdentifier(), socket.getOptions())) {
                                        // no problem, sending message
                                    } else {
                                        // this is bad, a previous instance is trying to open a socket
                                        logger.warn("Serious error.  There was an attempt to open a socket from a supposedly stale identifier:" + from +
                                                ". Current identifier is " + bindings.get(serializer.translateUp(socket.getIdentifier())) +
                                                " lower:" + socket.getIdentifier());
                                        socket.close();
                                        return;
                                    }


                                    // now write Success
                                    byte[] result = {SUCCESS};
                                    final ByteBuffer writeMe = ByteBuffer.wrap(result);
                                    new P2PSocketReceiver<LowerIdentifier>() {
                                        public void receiveException(P2PSocket<LowerIdentifier> socket, Exception ioe) {
                                            if (logger.isInfoEnabled())
                                                errorHandler.receivedException(socket.getIdentifier(), ioe);
                                        }

                                        public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                                            if (canRead) throw new IOException("Not expecting to read.");
                                            if (!canWrite) throw new IOException("Expecting to write.");

                                            if (socket.write(writeMe) == -1) {
                                                // socket closed
                                                if (logger.isInfoEnabled())
                                                    errorHandler.receivedException(socket.getIdentifier(), new ClosedChannelException("Error on incoming socket."));
                                                return;
                                            }

                                            if (writeMe.hasRemaining()) {
                                                // need to read more
                                                socket.register(false, true, this);
                                                return;
                                            }

                                            // done writing pass up socket with the newOptions
                                            final P2PSocket<LowerIdentifier> returnMe = new SocketWrapperSocket<>(
                                                    socket.getIdentifier(), socket, errorHandler, newOptions);
                                            callback.incomingSocket(returnMe);
                                        }
                                    }.receiveSelectResult(socket, false, true); // write SUCCESS

                                }
                            }.receiveSelectResult(socket, true, false);  // read the FROM
                        }
                    }.receiveSelectResult(socket, true, false); // read the TO (if wantsToVerify)
                }
            }.receiveSelectResult(s, true, false); // read wantsToVerify
        }

        /**
         * Head the message with the expected identifier
         */
        public MessageRequestHandle<LowerIdentifier, ByteBuffer> sendMessage(
                final LowerIdentifier i,
                ByteBuffer m,
                final MessageCallback<LowerIdentifier, ByteBuffer> deliverAckToMe,
                final Map<String, Object> options) {

            if (logger.isDebugEnabled()) {
                byte[] b = new byte[m.remaining()];
                System.arraycopy(m.array(), m.position(), b, 0, b.length);
                logger.debug("sendMessage(" + i + "," + m + ")" + Arrays.toString(b));
            }

            // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?
            // May need to re-think this SRHI at all the layers
            final MessageRequestHandleImpl<LowerIdentifier, ByteBuffer> ret =
                    new MessageRequestHandleImpl<>(i, m, options);

//      Integer index = null;
//      if (options != null) {
//        index = options.get(NODE_HANDLE_FROM_INDEX);
//      }

            byte[] msgWithHeader;
            if (options.containsKey(NODE_HANDLE_FROM_INDEX)) {
                // don't include an id
                msgWithHeader = new byte[1 + localIdentifier.length + m.remaining()];
                msgWithHeader[0] = NO_ID;

                System.arraycopy(localIdentifier, 0, msgWithHeader, 1, localIdentifier.length); // write the FROM
                m.get(msgWithHeader, 1 + localIdentifier.length, m.remaining()); // write the message

//        m.get(msgWithHeader, 1, m.remaining());
            } else {
                // don't include an id
                UpperIdentifier dest = getIntendedDest(options);

                if (dest == null) {
                    if (deliverAckToMe != null)
                        deliverAckToMe.sendFailed(ret, new MemoryExpiredException("No record of the upper identifier for " + i + " index=" + options.get(NODE_HANDLE_FROM_INDEX)));
                    return ret;
                }

                if (addBinding(dest, i, options)) {
                    // no problem, sending message
                } else {
                    deliverAckToMe.sendFailed(ret, new NodeIsFaultyException(i, m));
                    return ret;
                }

                byte[] destBytes;
                try {
                    SimpleOutputBuffer sob = new SimpleOutputBuffer((int) (localIdentifier.length * 2.5)); // good estimate
                    serializer.serialize(sob, dest);
                    destBytes = sob.getBytes();
                } catch (IOException ioe) {
                    deliverAckToMe.sendFailed(ret, ioe);
                    return ret;
                }

                // build a new ByteBuffer with the header
                msgWithHeader = new byte[1 + destBytes.length + localIdentifier.length + m.remaining()];
                msgWithHeader[0] = NORMAL; // write the TYPE
                System.arraycopy(destBytes, 0, msgWithHeader, 1, destBytes.length); // write the TO
                System.arraycopy(localIdentifier, 0, msgWithHeader, 1 + destBytes.length, localIdentifier.length); // write the FROM
                m.get(msgWithHeader, 1 + destBytes.length + localIdentifier.length, m.remaining()); // write the message
            }


            final ByteBuffer buf = ByteBuffer.wrap(msgWithHeader);

            ret.setSubCancellable(tl.sendMessage(i, buf, new MessageCallback<LowerIdentifier, ByteBuffer>() {

                public void ack(MessageRequestHandle<LowerIdentifier, ByteBuffer> msg) {
                    if (ret.getSubCancellable() != null && msg != ret.getSubCancellable())
                        throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" +
                                msg + " sub:" + ret.getSubCancellable());
                    if (deliverAckToMe != null) deliverAckToMe.ack(ret);
                }

                public void sendFailed(MessageRequestHandle<LowerIdentifier, ByteBuffer> msg, Exception ex) {
                    if (ret.getSubCancellable() != null && msg != ret.getSubCancellable())
                        throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:" +
                                msg + " sub:" + ret.getSubCancellable());
                    if (deliverAckToMe == null) {
                        errorHandler.receivedException(i, ex);
                    } else {
                        deliverAckToMe.sendFailed(ret, ex);
                    }
                }

            }, options));
            return ret;
        }

        public void messageReceived(LowerIdentifier i, ByteBuffer m, Map<String, Object> options) throws IOException {
            Map<String, Object> newOptions = OptionsFactory.copyOptions(options);

            byte msgType = m.get();
            logger.debug("messageReceived(" + i + "," + m + "):" + msgType);
            switch (msgType) {
                case NORMAL:
                    // this is a normal message, make sure it's for me
                    byte[] dest = new byte[localIdentifier.length];
                    m.get(dest);
                    if (!Arrays.equals(dest, localIdentifier)) {
                        // send back an error
                        logger.info(
                                "received message for wrong node from:" + i +
                                        " intended:" + Arrays.toString(dest) +
                                        " me:" + Arrays.toString(localIdentifier));

                        byte[] errorMessage = new byte[1 + localIdentifier.length];
                        errorMessage[0] = INCORRECT_IDENTITY;
                        System.arraycopy(localIdentifier, 0, errorMessage, 1, localIdentifier.length);
                        ByteBuffer buf = ByteBuffer.wrap(errorMessage);
                        tl.sendMessage(i, buf, null, options);   // it's important to the rendezvous layer to reuse the options
                        return;
                    }

                case NO_ID:
                    SimpleInputBuffer sib = new SimpleInputBuffer(m.array(), m.position());
                    UpperIdentifier from = serializer.deserialize(sib, i);
                    m.position(m.array().length - sib.bytesRemaining());

                    if (addBinding(from, i, options)) {
//            from = serializer.coalesce(from);

                        // need to do this so the boostrapper knows the proper identity
//            overrideLiveness.setLiveness(i, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(from)));
                    } else {
                        logger.warn("Warning.  Received message from stale identifier:" +
                                from + ". Current identifier is " + bindings.get(serializer.translateUp(i)) + " lower:" + i + " Probably a delayed message, dropping.");
                        errorHandler.receivedUnexpectedData(i, m.array(), m.position(), newOptions);
                        return;
                    }

                    newOptions.put(NODE_HANDLE_FROM_INDEX, from);
                    // continue to read the rest of the message

                    // it's for me, no problem
                    if (logger.isDebugEnabled()) {
                        byte[] b = new byte[m.remaining()];
                        System.arraycopy(m.array(), m.position(), b, 0, b.length);
                        logger.debug("received message for me from:" + from + "(" + from + "(" + i + ")) " + Arrays.toString(b));
                    }
                    callback.messageReceived(i, m, newOptions);
                    break;
                case INCORRECT_IDENTITY:
                    // it's an error, read it in
                    UpperIdentifier oldDest = bindings.get(serializer.translateUp(i));

                    UpperIdentifier newDest = serializer.deserialize(new SimpleInputBuffer(m.array(), m.position()), i);
                    logger.info("received INCORRECT_IDENTITY:" + i + " old:" + oldDest + " new:" + newDest);

                    if (addBinding(newDest, i, options)) { // should call setDeadForever

//            newDest = serializer.coalesce(newDest);

                        // need to do this so the boostrapper knows the proper identity
//            overrideLiveness.setLiveness(i, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(newDest)));
                        //          upper.notifyLivenessListeners(newDest, LIVENESS_ALIVE, options);
                    }
            }
        }

        public void acceptMessages(boolean b) {
            tl.acceptMessages(b);
        }

        public void acceptSockets(boolean b) {
            tl.acceptMessages(b);
        }

        public LowerIdentifier getLocalIdentifier() {
            return tl.getLocalIdentifier();
        }

        public void setCallback(TransportLayerCallback<LowerIdentifier, ByteBuffer> callback) {
            this.callback = callback;
        }

        public void setErrorHandler(ErrorHandler<LowerIdentifier> handler) {
            this.errorHandler = handler;
        }

        public void destroy() {
            logger.info("destroy()");
            tl.destroy();
        }
    }

    class UpperIdentityImpl implements
            UpperIdentity<UpperIdentifier, UpperMsgType>,
            TransportLayerCallback<MiddleIdentifier, UpperMsgType>,
            LivenessListener<MiddleIdentifier>,
            ProximityListener<MiddleIdentifier> {
        final List<LivenessListener<UpperIdentifier>> livenessListeners = new ArrayList<>();
        final Collection<ProximityListener<UpperIdentifier>> proxListeners =
                new ArrayList<>();
        private final Logger logger = LoggerFactory.getLogger(UpperIdentityImpl.class);
        // todo, make this a timer weak hash map
        protected Map<UpperIdentifier, Integer> liveness = new HashMap<>();
        TransportLayer<MiddleIdentifier, UpperMsgType> tl;
        ProximityProvider<MiddleIdentifier> prox;
        private ErrorHandler<UpperIdentifier> errorHandler;
        private TransportLayerCallback<UpperIdentifier, UpperMsgType> callback;
        private LivenessProvider<MiddleIdentifier> livenessProvider;
        private UpperIdentifier localIdentifier;

        public UpperIdentityImpl(
                UpperIdentifier local,
                TransportLayer<MiddleIdentifier, UpperMsgType> tl,
                LivenessProvider<MiddleIdentifier> live,
                ProximityProvider<MiddleIdentifier> prox) {
            this.localIdentifier = local;
            this.tl = tl;
            this.livenessProvider = live;
            this.prox = prox;

            tl.setCallback(this);
            livenessProvider.addLivenessListener(this);
            prox.addProximityListener(this);
        }

        public void clearState(UpperIdentifier i) {
            livenessProvider.clearState(serializer.translateDown(i));
        }

        public SocketRequestHandle<UpperIdentifier> openSocket(
                final UpperIdentifier i,
                final SocketCallback<UpperIdentifier> deliverSocketToMe,
                final Map<String, Object> options) {
            logger.debug("openSocket(" + i + "," + deliverSocketToMe + "," + options + ")");

            final SocketRequestHandleImpl<UpperIdentifier> handle = new SocketRequestHandleImpl<>(i, options);

            MiddleIdentifier middle = serializer.translateDown(i);

            if (addBinding(i, null, options)) {
                // no problem, sending message
            } else {
                deliverSocketToMe.receiveException(handle, new NodeIsFaultyException(i));
                return handle;
            }

//      synchronized(deadForever) {
//        if (deadForever.contains(i)) {
//          deliverSocketToMe.receiveException(handle, new NodeIsFaultyException(i));
//          return handle;
//        }
//      }

            Map<String, Object> newOptions = OptionsFactory.copyOptions(options);
            newOptions.put(NODE_HANDLE_FROM_INDEX, i);


            handle.setSubCancellable(tl.openSocket(middle, new SocketCallback<MiddleIdentifier>() {
                public void receiveException(SocketRequestHandle<MiddleIdentifier> s, Exception ex) {
                    deliverSocketToMe.receiveException(handle, ex);
                }

                public void receiveResult(SocketRequestHandle<MiddleIdentifier> cancellable, P2PSocket<MiddleIdentifier> sock) {
                    deliverSocketToMe.receiveResult(handle, new SocketWrapperSocket<>(i, sock, errorHandler, options));
                }
            }, newOptions));
            return handle;
        }

        /**
         * Synchronization by selector thread.  Make sure setDeadForever() is only called on selector, otherwise the
         * next layer down could get stuck trying to send to dead-forever node.
         */
        public MessageRequestHandle<UpperIdentifier, UpperMsgType> sendMessage(
                UpperIdentifier i,
                UpperMsgType m,
                MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe,
                Map<String, Object> options) {
            if (!environment.getSelectorManager().isSelectorThread())
                throw new RuntimeException("Must be called on selector thread.");
            logger.debug("sendMessage(" + i + "," + m + "," + options + ")");

            // how to synchronized this properly?  It's too bad that we have to hold the lock for the calls into the lower levels.
            // an alternative would be to re-synchronized and check again, and immeadiately cancel the message
            if (deadForever.contains(i)) {
                MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh =
                        new MessageRequestHandleImpl<>(i, m, options);
                deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
                return mrh;
            }

            IdentityMessageHandle ret;

            MiddleIdentifier middle = serializer.translateDown(i);
            if (addBinding(i, null, options)) {
                // no problem, sending message
            } else {
                MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh =
                        new MessageRequestHandleImpl<>(i, m, options);
                deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
                return mrh;
            }

//      synchronized(deadForever) {
//        if (deadForever.contains(i)) {
//          MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh =
//            new MessageRequestHandleImpl<UpperIdentifier, UpperMsgType>(i, m, options);
//          deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
//          return mrh;
//        }

            options = OptionsFactory.copyOptions(options);
            options.put(NODE_HANDLE_FROM_INDEX, i);
            ret = new IdentityMessageHandle(i, m, options, deliverAckToMe);
            addPendingMessage(i, ret);
//      }

            // the synchronization problem is here, if it goes dead now, then this message won't
            // be cancelled... TODO: get this correct, it's probably currently broken, maybe
            // need cancelled bit in ret so that it knows it's cancelled.
            ret.setSubCancellable(tl.sendMessage(middle, m, ret, options));
            return ret;
        }

        public void incomingSocket(P2PSocket<MiddleIdentifier> s) throws IOException {
            logger.debug("incomingSocket(" + s + ")");
//      int index = s.getOptions().get(NODE_HANDLE_FROM_INDEX);
            final UpperIdentifier from = getIntendedDest(s.getOptions()); //intendedDest.get(index).get();

            if (from == null) {
                errorHandler.receivedException(null, new MemoryExpiredException("No record of the upper identifier for " + s.getIdentifier() + " index=" + s.getOptions().get(NODE_HANDLE_FROM_INDEX)));
                s.close();
                return;
            }


            if (sanityChecker.isSane(from, s.getIdentifier())) {
                callback.incomingSocket(new SocketWrapperSocket<>(from, s, errorHandler, s.getOptions()));
            } else {
                logger.warn(
                        "incomingSocket() Sanity checker did not match " + from + " to " + s.getIdentifier() + " options:" + s.getOptions(),
                        new Exception("Stack Trace"));
                s.close();
            }
        }

        public void messageReceived(MiddleIdentifier i, UpperMsgType m, Map<String, Object> options) throws IOException {
            logger.debug("messageReceived(" + i + "," + m + "," + options + ")");
            final UpperIdentifier from = getIntendedDest(options); //intendedDest.get(index).get();

            if (from == null) {
                errorHandler.receivedException(null, new MemoryExpiredException("No record of the upper identifier for " + i + " index=" + options.get(NODE_HANDLE_FROM_INDEX) + " dropping message" + m));
                return;
            }

            if (sanityChecker.isSane(from, i)) {
                callback.messageReceived(from, m, options);
            } else {
                logger.warn(
                        "messageReceived() Sanity checker did not match " + from + " to " + i + " options:" + options,
                        new Exception("Stack Trace"));
            }
        }

        public boolean checkLiveness(UpperIdentifier i, Map<String, Object> options) {
            logger.debug("checkLiveness(" + i + "," + options + ")");
            if (deadForever.contains(i)) return false;

            options = OptionsFactory.copyOptions(options);
            options.put(NODE_HANDLE_FROM_INDEX, i);
            return livenessProvider.checkLiveness(serializer.translateDown(i), options);
        }

        public void addLivenessListener(LivenessListener<UpperIdentifier> name) {
            synchronized (livenessListeners) {
                livenessListeners.add(name);
            }
        }

        public boolean removeLivenessListener(LivenessListener<UpperIdentifier> name) {
            synchronized (livenessListeners) {
                return livenessListeners.remove(name);
            }
        }

        public int getLiveness(UpperIdentifier i, Map<String, Object> options) {
            logger.debug("getLiveness(" + i + "," + options + ")");
            if (deadForever.contains(i)) return LIVENESS_DEAD_FOREVER;
            options = OptionsFactory.copyOptions(options);
            options.put(NODE_HANDLE_FROM_INDEX, i);

            return livenessProvider.getLiveness(serializer.translateDown(i), options);
        }

        public void livenessChanged(MiddleIdentifier i, int val, Map<String, Object> options) {
            if (deadForever.contains(i)) {
                if (val < LIVENESS_DEAD) {
                    logger.error("Node " + i + " came back from the dead!  It's a miracle! " + val + " Ignoring.");
                }
                return;
            }

            UpperIdentifier upper = getIntendedDest(options);
//      if (val >= LIVENESS_DEAD_FOREVER) {
//        // options is required to have the NODE_HANDLE_FROM_INDEX
//        if (!options.containsKey(NODE_HANDLE_FROM_INDEX)) throw new IllegalArgumentException("Options doesn't have NODE_HANDLE_INDEX"+options);
//        upper = intendedDest.get(options.get(NODE_HANDLE_FROM_INDEX)).get();
            if (upper == null) {
                logger.warn("Memory for index " + options.get(NODE_HANDLE_FROM_INDEX) + " collected suppressing livenessChanged()", new Exception("Stack Trace"));
                return;
            }
//      } else {
//        if (options.containsKey(NODE_HANDLE_FROM_INDEX)) {
//          upper = intendedDest.get(options.get(NODE_HANDLE_FROM_INDEX)).get();
//        }
//      }
//      if (upper == null) {
//        upper = serializer.translateUp(i);
//      }
            setLiveness(upper, val, options);
        }

        /**
         * This is a guard so we don't notify about changes in liveness too many times.
         *
         * @param i
         * @param val
         * @param options
         */
        protected void setLiveness(UpperIdentifier i, int val, Map<String, Object> options) {
            // handle the first time
            int oldLiveness = -55;
            if (liveness.containsKey(i)) {
                oldLiveness = liveness.get(i);
            }

            if (val != oldLiveness) {
                liveness.put(i, val);
                notifyLivenessListeners(i, val, options);
            }
        }

        protected void notifyLivenessListeners(UpperIdentifier i, int liveness, Map<String, Object> options) {
            logger.debug("notifyLivenessListeners(" + i + "," + liveness + ")");
            List<LivenessListener<UpperIdentifier>> temp;
            synchronized (livenessListeners) {
                temp = new ArrayList<>(livenessListeners);
            }
            for (LivenessListener<UpperIdentifier> listener : temp) {
                listener.livenessChanged(i, liveness, options);
            }
        }

        public void addProximityListener(ProximityListener<UpperIdentifier> name) {
            synchronized (proxListeners) {
                proxListeners.add(name);
            }
        }

        public boolean removeProximityListener(ProximityListener<UpperIdentifier> name) {
            synchronized (proxListeners) {
                return proxListeners.remove(name);
            }
        }

        public int proximity(UpperIdentifier i, Map<String, Object> options) {
            logger.debug("proximity(" + i + ")");
            if (deadForever.contains(i)) return Integer.MAX_VALUE;
            return prox.proximity(serializer.translateDown(i), OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, i));
        }

        public void proximityChanged(MiddleIdentifier i, int newProx, Map<String, Object> options) {
            UpperIdentifier upper = getIntendedDest(options);
            if (upper == null) {
                logger.warn("Memory for " + options.get(NODE_HANDLE_FROM_INDEX) + " collected suppressing proximityChanged()", new Exception("Stack Trace"));
                return;
            }

            notifyProximityListeners(upper, newProx, options);
//      notifyProximityListeners(reverseIntendedDest.get(options.get(NODE_HANDLE_FROM_INDEX))serializer.translateUp(i), newProx, options);    
        }

        private void notifyProximityListeners(UpperIdentifier i, int newProx, Map<String, Object> options) {
            logger.debug("notifyProximityListeners(" + i + "," + newProx + ")");
            List<ProximityListener<UpperIdentifier>> temp;
            synchronized (proxListeners) {
                temp = new ArrayList<>(proxListeners);
            }
            for (ProximityListener<UpperIdentifier> listener : temp) {
                listener.proximityChanged(i, newProx, options);
            }
        }

//    public boolean ping(UpperIdentifier i, Map<String, Object> options) {      
//      if (logger.level <= Logger.FINE) logger.log("ping("+i+","+options+")");
//      if (deadForever.contains(i)) return false;
//      
//      return tl.ping(i, options);
//    }

        public void acceptMessages(boolean b) {
            tl.acceptMessages(b);
        }

        public void acceptSockets(boolean b) {
            tl.acceptSockets(b);
        }

        public UpperIdentifier getLocalIdentifier() {
            return localIdentifier;
        }

        public void setCallback(TransportLayerCallback<UpperIdentifier, UpperMsgType> callback) {
            this.callback = callback;
        }

        public void setErrorHandler(ErrorHandler<UpperIdentifier> handler) {
            this.errorHandler = handler;
        }

        public void destroy() {
            logger.info("destroy()");
            tl.destroy();
        }
    }

    class IdentityMessageHandle implements MessageRequestHandle<UpperIdentifier, UpperMsgType>, MessageCallback<MiddleIdentifier, UpperMsgType> {

        private Cancellable subCancellable;
        private UpperIdentifier identifier;
        private UpperMsgType message;
        private Map<String, Object> options;
        private MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe;

        public IdentityMessageHandle(
                UpperIdentifier identifier,
                UpperMsgType message, Map<String, Object> options,
                MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe) {
            this.identifier = identifier;
            this.message = message;
            this.options = options;
            this.deliverAckToMe = deliverAckToMe;
        }

        public UpperIdentifier getIdentifier() {
            return identifier;
        }

        public UpperMsgType getMessage() {
            return message;
        }

        public Map<String, Object> getOptions() {
            return options;
        }

        void deadForever() {
            cancel();
            if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, new NodeIsFaultyException(identifier, message));
        }

        public boolean cancel() {
            removePendingMessage(identifier, this);
            return subCancellable.cancel();
        }

        public Cancellable getSubCancellable() {
            return subCancellable;
        }

        public void setSubCancellable(Cancellable cancellable) {
            this.subCancellable = cancellable;
        }

        public void ack(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg) {
            removePendingMessage(identifier, this);
            if (deliverAckToMe != null) deliverAckToMe.ack(this);
        }

        public void sendFailed(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg, Exception reason) {
            removePendingMessage(identifier, this);
            if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, reason);
        }

        public String toString() {
            return "IdMsgHdl{" + message + "}->" + identifier;
        }
    }
}
