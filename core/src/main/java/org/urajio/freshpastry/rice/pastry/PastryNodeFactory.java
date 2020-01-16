package org.urajio.freshpastry.rice.pastry;

import org.urajio.freshpastry.rice.environment.Environment;

import java.io.IOException;

/**
 * The interface to an object which can construct PastryNodes.
 *
 * @author Andrew Ladd
 * @author Alan Mislove
 * @author Merziyah Poonawala
 * @author Abhishek Ray
 * @version $Id$
 */
public abstract class PastryNodeFactory {
    // max number of handles stored per routing table entry
    protected final byte rtMax;

    // leafset size
    protected final byte lSetSize;

    protected final byte rtBase;

    protected Environment environment;

    public PastryNodeFactory(Environment env) {
        this.environment = env;
        rtMax = (byte) environment.getParameters().getInt("pastry_rtMax");
        rtBase = (byte) environment.getParameters().getInt("pastry_rtBaseBitLength");
        lSetSize = (byte) environment.getParameters().getInt("pastry_lSetSize");
    }

    /**
     * Call this to construct a new node of the type chosen by the factory.
     *
     * @param bootstrap The node handle to bootstrap off of
     * @deprecated use newNode() then call PastryNode.boot(address);
     */
    public abstract PastryNode newNode(NodeHandle bootstrap);

    public abstract PastryNode newNode() throws IOException;

    /**
     * Call this to construct a new node of the type chosen by the factory, with
     * the given nodeId.
     *
     * @param bootstrap The node handle to bootstrap off of
     * @param nodeId    The nodeId of the new node
     * @deprecated use newNode(nodeId) then call PastryNode.boot(address);
     */
    public abstract PastryNode newNode(NodeHandle bootstrap, Id nodeId);

    public abstract PastryNode newNode(Id nodeId) throws IOException;

    public Environment getEnvironment() {
        return environment;
    }
}
