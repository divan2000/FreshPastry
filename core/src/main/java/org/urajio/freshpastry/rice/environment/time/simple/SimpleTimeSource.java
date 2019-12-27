package org.urajio.freshpastry.rice.environment.time.simple;

import org.urajio.freshpastry.rice.environment.time.TimeSource;


/**
 * Uses System.currentTimeMillis() to generate time.
 *
 * @author Jeff Hoye
 */
public class SimpleTimeSource implements TimeSource {
//  SelectorManager selector;

    public SimpleTimeSource(/*SelectorManager sm*/) {
        //  this.selector = sm;
    }

    /**
     * Returns the System.currentTimeMillis();
     */
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public void sleep(long delay) throws InterruptedException {
        Thread.sleep(delay);
    }

    public void destroy() {

    }

//  public void wait(Object lock, int timeToWait) throws InterruptedException {
//    if (selector.isSelectorThread()) throw new IllegalStateException("You can't call this on the selector thread.");
//    lock.wait(timeToWait);
//  }    
}
