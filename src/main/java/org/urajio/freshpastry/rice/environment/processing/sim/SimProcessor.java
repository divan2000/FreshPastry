package org.urajio.freshpastry.rice.environment.processing.sim;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Executable;
import org.urajio.freshpastry.rice.environment.processing.Processor;
import org.urajio.freshpastry.rice.environment.processing.WorkRequest;
import org.urajio.freshpastry.rice.environment.processing.simple.ProcessingRequest;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

public class SimProcessor implements Processor {
    SelectorManager selector;

    public SimProcessor(SelectorManager selector) {
        this.selector = selector;
    }

    public <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command,
                                                        SelectorManager selector, TimeSource ts) {
        return process(task, command, 0, selector, ts);
    }

    public <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command, int priority,
                                                        SelectorManager selector, TimeSource ts) {
        ProcessingRequest ret = new ProcessingRequest(task, command, 0, 0, ts, selector);
        selector.invoke(ret);
        return ret;
    }

    public Cancellable processBlockingIO(WorkRequest request) {
        selector.invoke(request);
        return request;
    }

    public void destroy() {
        // TODO Auto-generated method stub

    }

}
