package org.urajio.freshpastry.rice.environment.processing.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Executable;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

/**
 * Scheduling with a lower priority number will be executed first (is higher Priority)
 * 
 * @author Jeff Hoye
 */
@SuppressWarnings("unchecked")
public class ProcessingRequest implements Runnable,
    Comparable<ProcessingRequest>, Cancellable {
  private final static Logger logger = LoggerFactory.getLogger(ProcessingRequest.class);

  Continuation c;
  Executable r;
  private boolean cancelled = false;
  private boolean running = false;
  
  TimeSource timeSource;
  SelectorManager selectorManager;
  int priority = 0;
  long seq;

  public ProcessingRequest(Executable r, Continuation c, int priority, long seq,
      TimeSource timeSource, SelectorManager selectorManager) {
    this.r = r;
    this.c = c;

    this.timeSource = timeSource;
    this.selectorManager = selectorManager;
    this.priority = priority;
    this.seq = seq;
  }

  public void returnResult(Object o) {
    c.receiveResult(o);
  }

  public void returnError(Exception e) {
    c.receiveException(e);
  }

  public int getPriority() {
    return priority;
  }

  public int compareTo(ProcessingRequest request) {
    if (priority == request.getPriority()) {
      if (seq > request.seq) return 1;
      return -1;
    }
    if (priority > request.getPriority()) return 1;
    return -1;
  }

  public void run() {
    if (cancelled) return;
    running = true;
    logger.debug("COUNT: Starting execution of " + this);
    try {
      long start = timeSource.currentTimeMillis();
      final Object result = r.execute();
      logger.debug("QT: " + (timeSource.currentTimeMillis() - start) + " " + r.toString());

      selectorManager.invoke(new Runnable() {
        public void run() {
          returnResult(result);
        }

        public String toString() {
          return "return ProcessingRequest for " + r + " to " + c;
        }
      });
    } catch (final Exception e) {
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnError(e);
        }

        public String toString() {
          return "return ProcessingRequest for " + r + " to " + c;
        }
      });
    }
    logger.debug("COUNT: Done execution of " + this);
  }

  public boolean cancel() {
    cancelled = true;
    return !running;
  }
}
