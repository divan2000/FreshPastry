package org.urajio.freshpastry.org.mpisws.p2p.transport.identity;

import java.util.Map;

public interface BindStrategy<UpperIdentifier, LowerIdentifier> {
    boolean accept(UpperIdentifier u, LowerIdentifier l, Map<String, Object> options);
}
