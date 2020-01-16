package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocketReceiver;

import java.io.IOException;
import java.nio.ByteBuffer;


public class Forwarder<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(Forwarder.class);

    SourceRoute sr;
    P2PSocket<Identifier> socka;
    P2PSocket<Identifier> sockb;

    public Forwarder(SourceRoute<Identifier> sr, P2PSocket<Identifier> socka, P2PSocket<Identifier> sockb) {
        this.sr = sr;
        this.socka = socka;
        this.sockb = sockb;

        new HalfPipe(socka, sockb);
        new HalfPipe(sockb, socka);
    }

    private class HalfPipe implements P2PSocketReceiver {
        P2PSocket from;
        P2PSocket to;
        ByteBuffer buf;
        boolean shutdownTo = false;

        public HalfPipe(P2PSocket from, P2PSocket to) {
            this.from = from;
            this.to = to;
            buf = ByteBuffer.allocate(1024);
            from.register(true, false, this);
        }

        public String toString() {
            return "HalfPipe " + from + "=>" + to;
        }

        public void receiveException(P2PSocket socket, Exception e) {
            logger.debug(this + " " + socket, e);
            from.close();
            to.close();
        }

        public void receiveSelectResult(P2PSocket socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead) {
                if (socket != from) throw new IOException("Expected to read from " + from + " got " + socket);
                long result = from.read(buf);
                if (result == -1) {
                    logger.debug(from + " has shut down input, shutting down output on " + to);
                    shutdownTo = true;
                    return;
                }
                logger.debug("Read " + result + " bytes from " + from);
                buf.flip();
                to.register(false, true, this);
            } else {
                if (canWrite) {
                    if (socket != to) throw new IOException("Expected to write to " + to + " got " + socket);

                    long result = to.write(buf);
                    if (result == -1) {
                        logger.debug(to + " has closed, closing " + from);
                        from.close();
                    }
                    logger.debug("Wrote " + result + " bytes to " + to);


                    if (buf.hasRemaining()) {
                        // keep writing
                        to.register(false, true, this);
                    } else {
                        if (shutdownTo) {
                            to.shutdownOutput();
                            return;
                        }
                        // read again
                        buf.clear();
                        from.register(true, false, this);
                    }
                } else {
                    throw new IOException("Didn't select for either " + socket + "," + canRead + "," + canWrite);
                }
            }
        }
    }
}
