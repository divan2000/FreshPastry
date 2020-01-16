package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.urajio.freshpastry.org.mpisws.p2p.transport.ClosedChannelException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocketReceiver;
import org.urajio.freshpastry.rice.Continuation;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Writes a ByteBuffer to the socket then calls receiveResult().
 * <p>
 * Used for writing headers.
 * <p>
 * If continuation is null, then it closes the socket when done, or if there is an error.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public class BufferWriter<Identifier> implements P2PSocketReceiver<Identifier> {
    ByteBuffer sizeBuf = null;
    ByteBuffer writeMe;
    Continuation<P2PSocket<Identifier>, Exception> continuation;

    /**
     * @param writeMe
     * @param socket
     * @param continuation
     * @param includeSizeHeader true if the size needs to be written in a header, false if it is a fixed size buffer
     */
    public BufferWriter(ByteBuffer writeMe, P2PSocket<Identifier> socket,
                        Continuation<P2PSocket<Identifier>, Exception> continuation, boolean includeSizeHeader) {
        this.writeMe = writeMe;
        this.continuation = continuation;
        if (includeSizeHeader) {
            sizeBuf = ByteBuffer.allocate(4);
            sizeBuf.asIntBuffer().put(writeMe.remaining());
            sizeBuf.clear();
        }
        try {
            receiveSelectResult(socket, false, true);
        } catch (IOException ioe) {
            receiveException(socket, ioe);
        }
    }

    public BufferWriter(ByteBuffer writeMe, P2PSocket<Identifier> socket,
                        Continuation<P2PSocket<Identifier>, Exception> continuation) {
        this(writeMe, socket, continuation, true);
    }

    public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
        if (continuation == null) {
            socket.close();
        } else {
            continuation.receiveException(ioe);
        }
    }

    public void receiveSelectResult(P2PSocket<Identifier> socket,
                                    boolean canRead, boolean canWrite) throws IOException {
//    System.out.println("BufferWriter.rsr()");
        if (sizeBuf != null && sizeBuf.hasRemaining()) {
            if (socket.write(sizeBuf) < 0) {
                receiveException(socket, new ClosedChannelException("Unexpected closure of channel to " + socket.getIdentifier()));
                return;
            }
        }
        if (socket.write(writeMe) < 0) {
            receiveException(socket, new ClosedChannelException("Unexpected closure of channel to " + socket.getIdentifier()));
            return;
        }
        if (writeMe.hasRemaining()) {
            socket.register(false, true, this);
            return;
        }

        if (continuation == null) {
            socket.close();
        } else {
            continuation.receiveResult(socket);
        }
    }
}
