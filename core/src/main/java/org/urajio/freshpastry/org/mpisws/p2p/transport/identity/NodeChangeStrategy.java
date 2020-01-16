package org.urajio.freshpastry.org.mpisws.p2p.transport.identity;

public interface NodeChangeStrategy<UpperIdentifier> {
    boolean canChange(UpperIdentifier oldDest, UpperIdentifier newDest);
}
