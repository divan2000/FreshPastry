package org.urajio.freshpastry.org.mpisws.p2p.transport.wire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.*;
import org.urajio.freshpastry.rice.selector.SelectionKeyHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;

public class SocketManager extends SelectionKeyHandler implements P2PSocket<InetSocketAddress>, SocketRequestHandle<InetSocketAddress> {
    private final static Logger logger = LoggerFactory.getLogger(SocketManager.class);

    // the key to read from
    protected SelectionKey key;

    // the channel we are associated with
    protected SocketChannel channel;

    protected TCPLayer tcp;
    protected P2PSocketReceiver<InetSocketAddress> reader, writer;
    InetSocketAddress addr;
    Map<String, Object> options;
    /**
     * becomes true before we deliver this to the SocketCallback, this invalidates the cancel() operation
     */
    boolean delivered = false;

    /**
     * Constructor which accepts an incoming connection, represented by the
     * selection key. This constructor builds a new SocketManager, and waits
     * until the greeting message is read from the other end. Once the greeting
     * is received, the manager makes sure that a socket for this handle is not
     * already open, and then proceeds as normal.
     */
    public SocketManager(TCPLayer tcp, SelectionKey serverKey) throws IOException {
        this.tcp = tcp;

        channel = ((ServerSocketChannel) serverKey.channel()).accept();
        channel.socket().setSendBufferSize(tcp.SOCKET_BUFFER_SIZE);
        channel.socket().setReceiveBufferSize(tcp.SOCKET_BUFFER_SIZE);
        channel.socket().setTcpNoDelay(tcp.TCP_NO_DELAY);

        channel.configureBlocking(false);
        addr = (InetSocketAddress) channel.socket().getRemoteSocketAddress();

        logger.debug("(SA) " + "Accepted incoming connection from " + addr);

        key = tcp.wire.environment.getSelectorManager().register(channel, this, 0);
    }

    /**
     * Constructor which creates an outgoing connection to the given node
     * handle using the provided address as a source route intermediate node.
     * This creates the connection by building the socket and sending
     * across the greeting message. Once the response greeting message is
     * received, everything proceeds as normal.
     */
    public SocketManager(final TCPLayer tcp, final InetSocketAddress addr, final SocketCallback<InetSocketAddress> c, Map<String, Object> options) throws IOException {
        this.tcp = tcp;
        this.options = options;
        this.addr = addr;

        channel = SocketChannel.open();
        channel.socket().setSendBufferSize(tcp.SOCKET_BUFFER_SIZE);
        channel.socket().setReceiveBufferSize(tcp.SOCKET_BUFFER_SIZE);
        if (tcp.wire.forceBindAddress && tcp.wire.bindAddress != null) {
            channel.socket().bind(new InetSocketAddress(tcp.wire.bindAddress.getAddress(), 0));
        }
        channel.configureBlocking(false);

        logger.debug("(SM) Initiating socket connection to " + addr);

        if (channel.connect(addr)) {
            key = tcp.wire.environment.getSelectorManager().register(channel, this, 0);
            delivered = true;
            logger.debug("delivering1 " + SocketManager.this);
            c.receiveResult(SocketManager.this, SocketManager.this);
        } else {
            key = tcp.wire.environment.getSelectorManager().register(channel, new SelectionKeyHandler() {

                @Override
                public void write(SelectionKey key) {
                    SocketManager.this.write(key);
                }

                @Override
                public void read(SelectionKey key) {
                    SocketManager.this.read(key);
                }

                @Override
                public void modifyKey(SelectionKey key) {
                    SocketManager.this.modifyKey(key);
                }

                /**
                 * Specified by the SelectionKeyHandler interface - calling this tells this
                 * socket manager that the connection has completed and we can now
                 * read/write.
                 *
                 * @param key The key which is connectable.
                 */
                public void connect(SelectionKey key) {
                    try {
                        // unregister interest in connecting to this socket
                        if (channel.finishConnect()) {
                            key = tcp.wire.environment.getSelectorManager().register(channel, SocketManager.this, key.interestOps() & ~SelectionKey.OP_CONNECT);
                            delivered = true;
                            logger.debug("delivering2 " + SocketManager.this);
                            tcp.wire.broadcastChannelOpened(addr, SocketManager.this.options, true);
                            c.receiveResult(SocketManager.this, SocketManager.this);
                        }
                    } catch (IOException e) {
                        if (c == null) {
                            tcp.wire.errorHandler.receivedException(addr, e);
                        } else {
                            c.receiveException(SocketManager.this, e);
                        }
                        close();
                    }
                }
            }, SelectionKey.OP_CONNECT);
        }
    }

    public String toString() {
        return "SM " + addr + " " + channel;
    }

    /**
     * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
        try {
            logger.debug("Closing " + this + " r:" + reader + " w:" + writer);
            logger.debug("Closing " + this + " r:" + reader + " w:" + writer, new Exception("Stack Trace"));

            if (key != null) {
                key.cancel();
                key.attach(null);
                key = null;
            } else {
                // we were already closed
                return;
            }

            if (channel != null) {
                channel.close();
            }
            tcp.socketClosed(this);

            tcp.wire.environment.getSelectorManager().invoke(new Runnable() {
                public void run() {
                    // notify the writer/reader because an intermediate layer may have closed the socket, and they need to know
                    if (writer != null) {
                        if (writer == reader) {
                            P2PSocketReceiver<InetSocketAddress> temp = writer;
                            writer = null;
                            reader = null;
                            temp.receiveException(SocketManager.this, new ClosedChannelException("Channel closed. " + SocketManager.this));
                        } else {
                            P2PSocketReceiver<InetSocketAddress> temp = writer;
                            writer = null;
                            temp.receiveException(SocketManager.this, new ClosedChannelException("Channel closed. " + SocketManager.this));
                        }
                    }

                    if (reader != null) {
                        if (tcp.isDestroyed()) return;
                        P2PSocketReceiver<InetSocketAddress> temp = reader;
                        reader = null;
                        temp.receiveException(SocketManager.this, new ClosedChannelException("Channel closed."));
                    }
                }
            });
        } catch (IOException e) {
            logger.error("ERROR: Recevied exception " + e + " while closing socket!");
        }
    }

    /**
     * Method which should change the interestOps of the handler's key. This
     * method should *ONLY* be called by the selection thread in the context of
     * a select().
     *
     * @param key The key in question
     */
    public synchronized void modifyKey(SelectionKey key) {
        int flag = 0;
        if (reader != null) {
            flag |= SelectionKey.OP_READ;
        }
        if (writer != null) {
            flag |= SelectionKey.OP_WRITE;
        }
        key.interestOps(flag);
    }

    /**
     * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
        P2PSocketReceiver<InetSocketAddress> temp = null;
        synchronized (this) {
            if (reader == null) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                return;
            }
            temp = reader;
            reader = null;
        } // synchronized(this)
        try {
            temp.receiveSelectResult(this, true, false);
        } catch (IOException ioe) {
            temp.receiveException(this, ioe);
        }
        tcp.wire.environment.getSelectorManager().modifyKey(key);
    }

    /**
     * Writes to the socket attached to this socket manager.
     *
     * @param key The selection key for this manager
     */
    public void write(SelectionKey key) {
        P2PSocketReceiver<InetSocketAddress> temp = null;
        synchronized (this) {
            if (writer == null) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                return;
            }
            temp = writer;
//      clearTimer(writer);
            writer = null;
        }
        try {
            temp.receiveSelectResult(this, false, true);
        } catch (IOException ioe) {
            temp.receiveException(this, ioe);
        }
        tcp.wire.environment.getSelectorManager().modifyKey(key);
    }

    public synchronized void register(final boolean wantToRead, final boolean wantToWrite, P2PSocketReceiver<InetSocketAddress> receiver) {
        logger.debug(this + ".register(" + (wantToRead ? "r" : "") + (wantToWrite ? "w" : "") + "," + receiver + ")");
        if (key == null) {
            ClosedChannelException cce = new ClosedChannelException("Socket " + addr + " " + SocketManager.this + " is already closed.");
            logger.info("Socket " + addr + " " + this + " is already closed.", cce);
            receiver.receiveException(this, cce);
            return;
        }
        // this check happens before setting the reader because we don't want to change any state if the exception is going ot be thrown
        // so don't put this check down below!
        if (wantToWrite) {
            if (channel.socket().isOutputShutdown()) {
                receiver.receiveException(this,
                        new ClosedChannelException("Socket " + addr + " " + SocketManager.this + " already shut down output."));
                return;
            }
            if (writer != null) {
                if (writer != receiver) {
                    throw new IllegalStateException("Already registered " + writer + " for writing, you can't register " + receiver + " for writing as well! SM:" + this);
                }
            }
        }

        if (wantToRead) {
            if (reader != null) {
                if (reader != receiver)
                    throw new IllegalStateException("Already registered " + reader + " for reading, you can't register " + receiver + " for reading as well!");
            }
            reader = receiver;
        }

        if (wantToWrite) {
            writer = receiver;
        }
        tcp.wire.environment.getSelectorManager().modifyKey(key);
    }

    /**
     * Method which initiates a shutdown of this socket by calling
     * shutdownOutput().  This has the effect of removing the manager from
     * the open list.
     */
    public void shutdownOutput() {
        boolean closeMe = false;
        synchronized (this) {
            if (key == null) {
                throw new IllegalStateException("Socket already closed.");
            }

            try {
                logger.debug("Shutting down output on app connection " + this);

                channel.socket().shutdownOutput();

                tcp.wire.environment.getSelectorManager().invoke(new Runnable() {
                    public void run() {
                        // notify the writer/reader because an intermediate layer may have closed the socket, and they need to know
                        if (writer != null) {
                            writer.receiveException(SocketManager.this, new ClosedChannelException("Channel shut down."));
                            writer = null;
                        }
                    }
                });

            } catch (IOException e) {
                logger.error("ERROR: Received exception " + e + " while shutting down output for socket " + this);
                closeMe = true;
            }
        }
        tcp.wire.environment.getSelectorManager().modifyKey(key);

        // close has it's own synchronization semantics, don't want to be holding a lock when calling
        if (closeMe) {
            close();
        }
    }

    public long read(ByteBuffer dst) throws IOException {
        if (key == null || channel.socket().isInputShutdown()) return -1;
        try {
            long ret = channel.read(dst);
            logger.debug(this + "read(" + ret + "):" + Arrays.toString(dst.array()));

            tcp.notifyRead(ret, addr);
            return ret;
        } catch (IOException ioe) {
            logger.debug(this + " error reading", ioe);
            logger.info(this + " error reading");
            close();
            ClosedChannelException throwMe = new ClosedChannelException("Socket closed");
            throwMe.initCause(ioe);
            throw throwMe;
        }
    }

    public long write(ByteBuffer src) throws IOException {
        if (key == null || channel.socket().isOutputShutdown()) return -1;
        try {
            long ret = channel.write(src);
            logger.debug(this + "write(" + ret + "):" + Arrays.toString(src.array()));

            tcp.notifyWrite(ret, addr);
            return ret;
        } catch (IOException ioe) {
            logger.debug(this + " error writing", ioe);
            close();
            throw ioe;
        }
    }

    public boolean cancel() {
        if (key == null) return false;
        if (delivered) throw new IllegalStateException(this + ".cancel() Can't cancel, already delivered");
        close();
        return true;
    }

    private void exceptionAndClose(IOException e) {
        tcp.wire.errorHandler.receivedException(addr, e);
        close();
    }

    public InetSocketAddress getIdentifier() {
        return addr;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public SocketChannel getSocketChannel() {
        tcp.wire.environment.getSelectorManager().cancel(key);
        return channel;
    }
}
