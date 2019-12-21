package org.urajio.freshpastry.rice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.selector.SelectorManager;

/**
 * Asynchronously receives the result to a given method call, using
 * the command pattern.
 * 
 * Implementations of this class contain the remainder of a computation
 * which included an asynchronous method call.  When the result to the
 * call becomes available, the receiveResult method on this command
 * is called.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Andreas Haeberlen
 */
@SuppressWarnings("unchecked")
public interface Continuation<R, E extends Exception> {

  /**
   * Called when a previously requested result is now available.
   *
   * @param result The result of the command.
   */
  void receiveResult(R result);

  /**
   * Called when an exception occurred as a result of the
   * previous command.
   *
   * @param exception The exception which was caused.
   */
  void receiveException(E exception);

  /**
   * This class is a Continuation provided for simplicity which
   * passes any errors up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveResult() method with the appropriate behavior.
   */
  abstract class StandardContinuation<R, E extends Exception> implements Continuation<R, E> {

    /**
     * The parent continuation
     */
    protected Continuation<R,E> parent;
    
    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public StandardContinuation(Continuation<R, E> continuation) {
      parent = continuation;
    }

    /**
     * Called when an exception occurred as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveResult() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(E result) {
      parent.receiveException(result);
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes any results up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveException() method with the appropriate behavior.
   */
  abstract class ErrorContinuation<R, E extends Exception> implements Continuation<R, E> {
    
    /**
    * The parent continuation
     */
    protected Continuation<R, E> parent;
    
    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public ErrorContinuation(Continuation<R, E> continuation) {
      parent = continuation;
    }
    
    /**
     * Called when an the result is available.  Simply passes the result
     * to the parent;
     *
     * @param result The result
     */
    public void receiveResult(R result) {
      parent.receiveResult(result);
    }
  }

  /**
   * This class is a Continuation provided for simplicity which
   * listens for any errors and ignores any success values.  This
   * Continuation is provided for testing convenience only and should *NOT* be
   * used in production environment.
   */
  class ListenerContinuation<R, E extends Exception> implements Continuation<R, E> {
    private final static Logger logger = LoggerFactory.getLogger(ListenerContinuation.class);
    /**
     * The name of this continuation
     */
    protected String name;
    

    /**
     * Constructor which takes in a name
     *
     * @param name A name which uniquely identifies this continuation for
     *   debugging purposes
     */
    public ListenerContinuation(String name, Environment env) {
      this.name = name;
    }
    
    /**
     * Called when a previously requested result is now available. Does
     * absolutely nothing.
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
    }

    /**
     * Called when an exception occurred as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      logger.warn("ERROR - Received exception during task " + name, result);
    }
  }
  
  /**
   * This class is a Continuation provided for simplicity which
   * passes both results and exceptions to the receiveResult() method.
   */
  abstract class SimpleContinuation implements Continuation {
    
    /**
     * Called when an exception occurred as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      receiveResult(result);
    }
  }

  /**
   * This class provides a continuation which is designed to be used from
   * an external thread.  Applications should construct this continuation pass it
   * in to the appropriate method, and then call sleep().  Once the thread is woken
   * up, the user should check exceptionThrown() to determine if an error was
   * caused, and then call getException() or getResult() as appropriate.
   */
  class ExternalContinuation<R, E extends Exception> implements Continuation<R, E> {

    protected Exception exception;
    protected Object result;
    protected boolean done = false;

    public synchronized void receiveResult(Object o) {
      result = o;
      done = true;
      notify();
    }

    public synchronized void receiveException(Exception e) {
      exception = e;
      done = true;
      notify();
    }

    public Object getResult() {
      if (exception != null) {
        throw new IllegalArgumentException("Exception was thrown in ExternalContinuation, but getResult() called!");
      }
        
      return result;
    }

    public Exception getException() {
      return exception;
    }

    public synchronized void sleep() {
      try {
        while (! done) {
          wait();
        }
      } catch (InterruptedException e) {
        exception = e;
      }
    }
    
    public boolean exceptionThrown() {
      return (exception != null);
    }
  }
  
  /**
   * This class is used when you want to run some task on the selector thread
   * and wait for it to return its result in a Continuation.  It is essentially
   * a convenience object which combines the functionality of a Runnable that
   * can be invoked on the Selector with an ExternalContinuation that it will
   * wait on.  Override the run(Continuation) method then call invoke() to
   * get the result or Exception from the operation.  The current thread will
   * block on invoke until the continuation returns a result or an exception.
   * 
   * @author jstewart
   *
   */
  abstract class ExternalContinuationRunnable<R, E extends Exception> implements Runnable {
    private ExternalContinuation<R,E> e;

    public ExternalContinuationRunnable() {
      e = new ExternalContinuation<>();
    }
    
    public void run() {
      try {
        execute(e);
      } catch (Exception exc) {
        e.receiveException(exc);
      }
    }
    
    protected abstract void execute(Continuation c) throws Exception;
    
    public Object invoke(SelectorManager sm) throws Exception {
      sm.invoke(this);
      e.sleep();
      if (e.exceptionThrown())
        throw e.getException();
      return e.getResult();
    }
 
    public Object invoke(Environment env) throws Exception {
      return invoke(env.getSelectorManager());
    }
  }

  /**
   * This class is used when you want to run some task on the selector thread
   * and wait for it to return its result.  Override execute() to perform the
   * operation and then use invoke to schedule its operation.  The current
   * thread will block until the operation returns a result or an exception.
   * 
   * @author jstewart
   *
   */
  abstract class ExternalRunnable extends ExternalContinuationRunnable {
    protected abstract Object execute();
    
    protected void execute(Continuation c) throws Exception {
      c.receiveResult(execute());
    }
  }

  /**
   * This class represents a Continuation which is used when multiple 
   * results are expected, which can come back at different times.  The
   * prototypical example of its use is in an application like Past, where
   * Insert messages are sent to a number of replicas and the responses
   * come back at different times.  
   *
   * Optionally, the creator can override the isDone() method, which 
   * is called each time an intermediate result comes in.  This allows
   * applications like Past to declare an insert successful after a 
   * certain number of results have come back successful.
   */
  class MultiContinuation {
  
    protected Object[] result;
    protected boolean[] haveResult;
    protected Continuation parent;
    protected boolean done;
    
    /**
     * Constructor which takes a parent continuation as well
     * as the number of results which to expect.  
     *
     * @param parent The parent continuation
     * @param num The number of results expected to come in
     */
    public MultiContinuation(Continuation parent, int num) {
      this.parent = parent;
      this.result = new Object[num];
      this.haveResult = new boolean[num];
      this.done = false;
    }
    
    /**
     * Returns the continuation which should be used as the
     * result continuation for the index-th result.  This should
     * be called exactly once for each int between 0 and num.
     *
     * @param index The index of this continuation
     */
    public Continuation getSubContinuation(final int index) {
      return new Continuation() {
        public void receiveResult(Object o) { receive(index, o); }
        public void receiveException(Exception e) { receive(index, e); }
      };
    }
    
    /**
     * Internal method which receives the results and determines
     * if we are done with this task.  This method ignores multiple
     * calls by the same client continuation.
     *
     * @param index The index the result is for
     * @param o The result for that continuation
     */
    protected void receive(int index, Object o) {
      if ((! done) && (! haveResult[index])) {
        haveResult[index] = true;
        result[index] = o;

        try {
          if (isDone()) {
            done = true;
            parent.receiveResult(getResult());
          }
        } catch (Exception e) {
          done = true;
          parent.receiveException(e);
        }
      }
    }
    
    /**
     * Method which returns whether or not we are done.  This is designed
     * to be overridden by subclasses in order to allow for more advanced
     * behavior.  
     *
     * If we are done and the subclass wishes to return an exception to the
     * calling application, it may throw an Exception, which will be caught
     * and returned to the parent via the receiveException() method.  This
     * will cause this continuation to be permanently marked as done.
     */
    public boolean isDone() throws Exception {
      for (boolean b : haveResult)
        if (!b)
          return false;
      
      return true;
    }
    
    /**
     * Method which can also be overridden to change what result should be 
     * returned to the parent continuation.  This defaults to the Object[]
     * containing results or exceptions.
     *
     * @return The result which should be returned to the application
     */
    public Object getResult() {
      return result; 
    }
  }
  
  /**
   * Continuation class which takes a provided string as it's name, and
   * returns that String when toString() is called.
   */
  class NamedContinuation implements Continuation {
    
    // the internal continuation
    protected Continuation parent;
    
    // the name of this continuation
    protected String name;
    
    /**
     * Builds a new NamedContinuation given the name and the wrapped
     * continuation
     *
     * @param name The name
     * @param command The parent continuation
     */
    public NamedContinuation(String name, Continuation command) {
      this.name = name;
      this.parent = command;
    }
    
    /**
     * Called when an the result is available.  Simply passes the result
     * to the parent;
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
      parent.receiveResult(result);
    }
    
    /**
     * Called when an exception occurred as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveException() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      parent.receiveException(result);
    }
    
    /**
     * Returns the name of this continuation
     *
     * @return The name
     */
    public String toString() {
      return name;
    }
  }
}
