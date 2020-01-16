package org.urajio.freshpastry.rice.pastry;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;

import java.io.IOException;

public interface NodeHandleFactory<NH extends NodeHandle> {
    NH readNodeHandle(InputBuffer buf) throws IOException;

    /**
     * Needed for legacy java deserialization of NodeHanlde because we aren't given any
     * other way to do this properly such as a protected constructor.
     *
     * @param handle
     * @return
     */
    NH coalesce(NH handle);

    void addNodeHandleFactoryListener(NodeHandleFactoryListener<NH> listener);

    void removeNodeHandleFactoryListener(NodeHandleFactoryListener<NH> listener);
}
