package org.urajio.freshpastry.rice.environment.processing.sim;

import org.urajio.freshpastry.rice.*;
import rice.environment.logging.LogManager;
import org.urajio.freshpastry.rice.environment.processing.*;
import org.urajio.freshpastry.rice.environment.processing.simple.ProcessingRequest;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

public class SimProcessor implements Processor {
  SelectorManager selector;

  public SimProcessor(SelectorManager selector) {
    this.selector = selector;
  }

  public <R, E extends Exception> Cancellable process(Executable<R,E> task, Continuation<R, E> command,
      SelectorManager selector, TimeSource ts, LogManager log) {
    return process(task, command, 0, selector, ts, log);
  }

  public <R, E extends Exception> Cancellable process(Executable<R,E> task, Continuation<R, E> command, int priority,
      SelectorManager selector, TimeSource ts, LogManager log) {
    ProcessingRequest ret = new ProcessingRequest(task, command, 0, 0, log, ts, selector);
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
