package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.urajio.freshpastry.org.mpisws.p2p.transport.ClosedChannelException;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocketReceiver;
import org.urajio.freshpastry.rice.Continuation;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reads a ByteBuffer to the socket then calls receiveResult().
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public class BufferReader<Identifier> implements P2PSocketReceiver<Identifier> {
    boolean readSize = false;
    int size = -1;
    ByteBuffer buf;
    Continuation<ByteBuffer, Exception> continuation;

    /**
     * Constructor for variable/unknown sized BB, it reads the size off the stream
     *
     * @param socket
     * @param continuation
     */
    public BufferReader(P2PSocket<Identifier> socket,
                        Continuation<ByteBuffer, Exception> continuation) {
        this(socket, continuation, -1);
    }

    /**
     * Constructor for fixed size BB
     *
     * @param socket
     * @param continuation
     * @param size         the fixed size buffer to read
     */
    public BufferReader(P2PSocket<Identifier> socket,
                        Continuation<ByteBuffer, Exception> continuation, int size) {
        this.continuation = continuation;
        this.size = size;
        if (size < 0) {
            buf = ByteBuffer.allocate(4);
        } else {
            buf = ByteBuffer.allocate(size);
        }
        try {
            receiveSelectResult(socket, true, false);
        } catch (IOException ioe) {
            receiveException(socket, ioe);
        }
    }

    public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
        continuation.receiveException(ioe);
    }

    public void receiveSelectResult(P2PSocket<Identifier> socket,
                                    boolean canRead, boolean canWrite) throws IOException {
//    System.out.println("BufferReader.rsr()");
        if (socket.read(buf) < 0) {
            receiveException(socket, new ClosedChannelException("Unexpected closure of channel to " + socket.getIdentifier()));
            return;
        }
        if (buf.hasRemaining()) {
            socket.register(true, false, this);
            return;
        }

        buf.flip();
        if (size < 0) {
            // we need to read the size from the buffer
            size = buf.asIntBuffer().get();
//      System.out.println("read size");
            buf = ByteBuffer.allocate(size);
            receiveSelectResult(socket, true, false);
        } else {
            continuation.receiveResult(buf);
        }
    }
}
