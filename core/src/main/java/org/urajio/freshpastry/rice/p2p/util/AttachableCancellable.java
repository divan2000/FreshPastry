package org.urajio.freshpastry.rice.p2p.util;

import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Allows you to cancel a group of things.
 * <p>
 * If you attach to a cancelled item, it gets cancelled now.
 *
 * @author Jeff Hoye
 */
public class AttachableCancellable implements Cancellable {

    /**
     * If subCancellable = null, it's been cancelled.
     */
    Collection<Cancellable> subCancellable = new ArrayList<>();

    public boolean isCancelled() {
        return subCancellable == null;
    }

    /**
     * Returns false if any are false;
     */
    public boolean cancel() {
        Collection<Cancellable> delme;
        synchronized (this) {
            if (subCancellable == null) return true;
            delme = subCancellable;
            subCancellable = null;
        }
        boolean ret = true;
        for (Cancellable c : delme) {
            if (!c.cancel()) ret = false;
        }
        return ret;
    }

    public void attach(Cancellable c) {
        if (c == null) return;
        boolean cancel = false;
        synchronized (this) {
            if (subCancellable == null) {
                cancel = true;
            } else {
                subCancellable.add(c);
            }
        }
        if (cancel) {
            c.cancel();
        }
    }

    public void detach(Cancellable c) {
        if (c == null) return;
        synchronized (this) {
            if (subCancellable == null) return;
            subCancellable.remove(c);
        }
    }
} 
