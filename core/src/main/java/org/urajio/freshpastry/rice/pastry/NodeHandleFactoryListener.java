package org.urajio.freshpastry.rice.pastry;

/**
 * Notified when ever we hear of a Node
 * <p>
 * Add the listener to the NodeHandleFactory
 *
 * @author Jeff Hoye
 */
public interface NodeHandleFactoryListener<NH extends NodeHandle> {
    void nodeHandleFound(NH nodeHandle);
}
