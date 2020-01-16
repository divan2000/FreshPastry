package org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.Id;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;

import java.io.IOException;

public interface IdFactory {
    Id build(InputBuffer buf) throws IOException;
}
