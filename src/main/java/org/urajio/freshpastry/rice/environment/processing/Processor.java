package org.urajio.freshpastry.rice.environment.processing;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.Executable;
import rice.environment.logging.LogManager;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

/**
 * Provides a mechanism to do time consuming tasks off of FreePastry's selecto thread.
 * 
 * Usually acquired by calling environment.getProcessor().
 * 
 * @author Jeff Hoye
 */
public interface Processor extends Destructable {
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  The continuation will
   * be called on the Selector thread.
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command, SelectorManager selector, TimeSource ts, LogManager log);
    
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  The continuation will
   * be called on the Selector thread. Passes a priority that will be respected if possible.  
   *
   * @param priority lower number is higher priority, 0 is default priority, and negative numbers are high priority
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command, int priority, SelectorManager selector, TimeSource ts, LogManager log);

  /**
   * Schedules a different type of task.  This thread is for doing Disk IO that is required to be blocking.
   * 
   * @param request
   */
  Cancellable processBlockingIO(WorkRequest request);
  
  /**
   * Shuts down the processing thread.
   */
  void destroy();
}
