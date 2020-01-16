package org.urajio.freshpastry.rice.pastry.dist;

import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.NodeIdFactory;
import org.urajio.freshpastry.rice.pastry.socket.SocketPastryNodeFactory;
import org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous.RendezvousSocketPastryNodeFactory;

import java.io.IOException;

/**
 * An abstraction of the nodeId factory for distributed nodes. In order to
 * obtain a nodeId factory, a client should use the getFactory method, passing
 * in either PROTOCOL_RMI or PROTOCOL_WIRE as the protocol, and the port number
 * the factory should use. In the wire protocol, the port number is the starting
 * port number that the nodes are constructed on, and in the rmi protocol, the
 * port number is the location of the local RMI registry.
 *
 * @author Alan Mislove
 * @version $Id: DistPastryNodeFactory.java,v 1.8 2003/12/22 03:24:46 amislove
 * Exp $
 */
public class DistPastryNodeFactory {

    // choices of protocols
    public static int PROTOCOL_SOCKET = 2;
    public static int PROTOCOL_RENDEZVOUS = 3;

    public static int PROTOCOL_DEFAULT = PROTOCOL_SOCKET;

    /**
     * Static method which is designed to be used by clients needing a distrubuted
     * pastry node factory. The protocol should be one of PROTOCOL_RMI or
     * PROTOCOL_WIRE. The port is protocol-dependent, and is the port number of
     * the RMI registry if using RMI, or is the starting port number the nodes
     * should be created on if using wire.
     *
     * @param protocol The protocol to use (PROTOCOL_RMI or PROTOCOL_WIRE)
     * @param port     The RMI registry port if RMI, or the starting port if wire.
     * @param nf       DESCRIBE THE PARAMETER
     * @return A DistPastryNodeFactory using the given protocol and port.
     * @throws IllegalArgumentException If protocol is an unsupported port.
     */
    public static SocketPastryNodeFactory getFactory(NodeIdFactory nf, int protocol, int port, Environment env) throws IOException {
        if (protocol == PROTOCOL_SOCKET) {
            return new SocketPastryNodeFactory(nf, port, env);
        }
        if (protocol == PROTOCOL_RENDEZVOUS) {
            return new RendezvousSocketPastryNodeFactory(nf, port, env, false);
        }

        throw new IllegalArgumentException("Unsupported Protocol " + protocol);
    }
}

