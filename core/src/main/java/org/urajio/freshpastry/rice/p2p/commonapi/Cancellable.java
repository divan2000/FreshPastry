package org.urajio.freshpastry.rice.p2p.commonapi;

public interface Cancellable {
    /**
     * @return true if it was cancelled, false if it was already complete, or cancelled.
     */
    boolean cancel();
}
