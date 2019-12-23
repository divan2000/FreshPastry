package org.urajio.freshpastry.rice.environment.time;

import org.urajio.freshpastry.rice.Destructable;

/**
 * Virtualized clock for FreePastry.
 * <p>
 * Can return the current time, or be blocked on.
 * <p>
 * Usually acquired by calling environment.getTimeSource().
 * <p>
 * TODO: add wait(lock, timeout) that is the same a s lock.wait(timeout)
 *
 * @author Jeff Hoye
 */
public interface TimeSource extends Destructable {
    /**
     * @return the current time in millis
     */
    long currentTimeMillis();

    /**
     * block for this many millis
     *
     * @param delay the amount of time to sleep
     * @throws InterruptedException
     */
    void sleep(long delay) throws InterruptedException;

    /**
     * This method has the same syntax as lock.wait(timeToWait), but works in the simulator.
     *
     * You need to be holding the lock on lock before calling this.
     * You may not call this on the SelectorThread
     *
     * @param lock
     * @param timeToWait
     * @throws InterruptedException
     */
//  public void wait(Object lock, int timeToWait) throws InterruptedException;
}
