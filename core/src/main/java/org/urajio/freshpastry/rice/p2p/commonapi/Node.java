package org.urajio.freshpastry.rice.p2p.commonapi;

import org.urajio.freshpastry.rice.environment.Environment;

/**
 * @author Alan Mislove
 * @author Peter Druschel
 * @version $Id$
 * @(#) Node.java
 * <p>
 * Interface which represents a node in a peer-to-peer system, regardless of
 * the underlying protocol.  This represents a factory, in a sense, that will
 * give a application an Endpoint which it can use to send and receive
 * messages.
 */
public interface Node {

    /**
     * This returns a Endpoint specific to the given application and
     * instance name to the application, which the application can then use in
     * order to send an receive messages.  This method abstracts away the
     * port number for this application, generating a port by hashing together
     * the class name with the instance name to generate a unique port.
     * <p>
     * Developers who wish for more advanced behavior can specify their
     * port manually, by using the second constructor below.
     *
     * @param application The Application
     * @param instance    An identifier for a given instance
     * @return The endpoint specific to this applicationk, which can be used for
     * message sending/receiving.  Endpoint is already registered.
     * @deprecated use buildEndpoint(), then call Endpoint.register(), fixes
     * synchronization problems, related to implicit behavior
     */
    @Deprecated
    Endpoint registerApplication(Application application, String instance);

    /**
     * Returns the Id of this node
     *
     * @return This node's Id
     */
    Id getId();

    /**
     * Returns a factory for Ids specific to this node's protocol.
     *
     * @return A factory for creating Ids.
     */
    IdFactory getIdFactory();

    /**
     * Returns a handle to the local node. This node handle is serializable, and
     * can therefore be sent to other nodes in the network and still be valid.
     *
     * @return A NodeHandle referring to the local node.
     */
    NodeHandle getLocalNodeHandle();

    /**
     * Returns the environment.  This allows the nodes to be virtualized within the JVM
     *
     * @return the environment for this node/app.
     */
    Environment getEnvironment();

    /**
     * Same as register application, but returns an unregistered Endpoint.  This allows
     * the application to finish initialization that may require the endpoint
     * before it receives messages from the network and notification of changes.
     * <p>
     * When then application is ready, it must call endpoint.register() to receive messages.
     *
     * @param application
     * @param instance
     * @return
     */
    Endpoint buildEndpoint(Application application, String instance);

    /**
     * For debugging: print the internal routing state of the Node.
     *
     * @return
     */
    String printRouteState();
}

