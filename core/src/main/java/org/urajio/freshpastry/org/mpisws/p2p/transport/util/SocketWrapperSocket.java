package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.ErrorHandler;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocketReceiver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;


/**
 * Just maps a socket from one form into another.
 *
 * @param <Identifier>
 * @param <SubIdentifier>
 * @author Jeff Hoye
 */
public class SocketWrapperSocket<Identifier, SubIdentifier> implements P2PSocket<Identifier>, P2PSocketReceiver<SubIdentifier> {
    private final static Logger logger = LoggerFactory.getLogger(SocketWrapperSocket.class);

    protected Identifier identifier;
    protected P2PSocket<SubIdentifier> socket;
    protected Map<String, Object> options;
    // TODO: make getters
    protected P2PSocketReceiver<Identifier> reader, writer;
    protected ErrorHandler<Identifier> errorHandler;

    public SocketWrapperSocket(Identifier identifier, P2PSocket<SubIdentifier> socket, ErrorHandler<Identifier> errorHandler, Map<String, Object> options) {
        this.identifier = identifier;
        this.socket = socket;
        this.options = options;
        this.errorHandler = errorHandler;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public void close() {
        logger.debug("Closing " + this, new Exception("Stack Trace"));
        socket.close();
    }

    public long read(ByteBuffer dsts) throws IOException {
        long ret = socket.read(dsts);
        logger.debug(this + "read():" + ret);
        return ret;
    }

    public void register(boolean wantToRead, boolean wantToWrite,
                         final P2PSocketReceiver<Identifier> receiver) {
        logger.debug(this + "register(" + wantToRead + "," + wantToWrite + "," + receiver + ")");
        if (wantToRead) {
            if (reader != null && reader != receiver)
                throw new IllegalStateException("Already registered " + reader + " for reading. Can't register " + receiver);
            reader = receiver;
        }
        if (wantToWrite) {
            if (writer != null && writer != receiver)
                throw new IllegalStateException("Already registered " + reader + " for writing. Can't register " + receiver);
            writer = receiver;
        }
        socket.register(wantToRead, wantToWrite, this);
    }

    public void receiveSelectResult(P2PSocket<SubIdentifier> socket, boolean canRead,
                                    boolean canWrite) throws IOException {
//    logger.log(this+"rsr("+socket+","+canRead+","+canWrite+")");
        logger.debug(this + "rsr(" + socket + "," + canRead + "," + canWrite + ")");
        if (canRead && canWrite && (reader == writer)) {
            P2PSocketReceiver<Identifier> temp = reader;
            reader = null;
            writer = null;
            temp.receiveSelectResult(this, canRead, canWrite);
            return;
        }

        if (canRead) {
            P2PSocketReceiver<Identifier> temp = reader;
            if (temp == null) {
                logger.warn("no reader in " + this + ".rsr(" + socket + "," + canRead + "," + canWrite + ")");
            } else {
                reader = null;
                temp.receiveSelectResult(this, true, false);
            }
        }

        if (canWrite) {
            P2PSocketReceiver<Identifier> temp = writer;
            if (temp == null) {
                logger.warn("no writer in " + this + ".rsr(" + socket + "," + canRead + "," + canWrite + ")");
            } else {
                writer = null;
                temp.receiveSelectResult(this, false, true);
            }
        }
    }

    public void receiveException(P2PSocket<SubIdentifier> socket, Exception e) {
        if (writer != null) {
            if (writer == reader) {
                P2PSocketReceiver<Identifier> temp = writer;
                writer = null;
                reader = null;
                temp.receiveException(this, e);
            } else {
                P2PSocketReceiver<Identifier> temp = writer;
                writer = null;
                temp.receiveException(this, e);
            }
        }

        if (reader != null) {
            P2PSocketReceiver<Identifier> temp = reader;
            reader = null;
            temp.receiveException(this, e);
        }
        if (reader == null && writer == null && errorHandler != null)
            errorHandler.receivedException(getIdentifier(), e);
    }


    public void shutdownOutput() {
        socket.shutdownOutput();
    }

    public long write(ByteBuffer srcs) throws IOException {
        long ret = socket.write(srcs);
        logger.debug(this + "write():" + ret);
        return ret;
    }

    @Override
    public String toString() {
        if (getIdentifier() == socket.getIdentifier()) return socket.toString();
        return identifier + "-" + socket;
    }

    public Map<String, Object> getOptions() {
        return options;
    }
}
