package org.urajio.freshpastry.rice.environment.processing;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

/**
 * Extend this class and implement doWork() if you need to do blocking disk IO.
 * <p>
 * This is primarily used by Persistence.
 *
 * @author Jeff Hoye
 */
public abstract class WorkRequest<R> implements Runnable, Cancellable {
    protected boolean cancelled = false;
    protected boolean running = false;
    private Continuation<R, Exception> c;
    private SelectorManager selectorManager;

    public WorkRequest(Continuation<R, Exception> c, SelectorManager sm) {
        this.c = c;
        this.selectorManager = sm;
    }

    public WorkRequest() {
        /* do nothing */
    }

    public void returnResult(R o) {
        c.receiveResult(o);
    }

    public void returnError(Exception e) {
        c.receiveException(e);
    }

    public void run() {
        if (cancelled) return;
        running = true;

        try {
            // long start = environment.getTimeSource().currentTimeMillis();
            final R result = doWork();
            // System.outt.println("PT: " + (environment.getTimeSource().currentTimeMillis() - start) + " " + toString());
            selectorManager.invoke(new Runnable() {
                public void run() {
                    returnResult(result);
                }

                public String toString() {
                    return "invc result of " + c;
                }
            });
        } catch (final Exception e) {
            selectorManager.invoke(new Runnable() {
                public void run() {
                    returnError(e);
                }

                public String toString() {
                    return "invc error of " + c;
                }
            });
        }
    }

    public boolean cancel() {
        cancelled = true;
        return !running;
    }

    public abstract R doWork() throws Exception;
}
