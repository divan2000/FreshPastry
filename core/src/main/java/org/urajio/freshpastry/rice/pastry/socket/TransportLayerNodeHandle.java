package org.urajio.freshpastry.rice.pastry.socket;

/**
 * Parallel interface to the CommonAPI NodeHandle, because it is an abstract object to gain the
 * observer pattern.
 *
 * @param <Identifier> the underlieing layer
 * @author Jeff Hoye
 */
public abstract class TransportLayerNodeHandle<Identifier> extends org.urajio.freshpastry.rice.pastry.NodeHandle {
    public abstract Identifier getAddress();

    //  public Id getId();
//  public void serialize(OutputBuffer sob) throws IOException;
    public abstract long getEpoch();
}
