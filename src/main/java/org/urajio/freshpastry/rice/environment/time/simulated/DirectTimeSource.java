package org.urajio.freshpastry.rice.environment.time.simulated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.selector.SelectorManager;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.util.ArrayList;
import java.util.HashSet;

public class DirectTimeSource implements TimeSource {
    private final static Logger logger = LoggerFactory.getLogger(DirectTimeSource.class);


    protected long time = 0;
    protected String instance;
    protected SelectorManager selectorManager;

    /**
     * When destry is called, throw an interrupted exception on all of these.
     */
    protected HashSet<BlockingTimerTask> pendingTimers = new HashSet<>();

    public DirectTimeSource(long time) {
        this(time, null);
    }

    public DirectTimeSource(long time, String instance) {
        if (time < 0) {
            time = System.currentTimeMillis();
        } else {
            this.time = time;
        }
        this.instance = instance;
    }

    public DirectTimeSource(Parameters p) {
        this(p.getLong("direct_simulator_start_time"));
    }

    // TODO: dsdiv remove this method
    public void setLogManager() {
    }

    public void setSelectorManager(SelectorManager sm) {
        selectorManager = sm;

    }

    public long currentTimeMillis() {
        return time;
    }

    /**
     * Should be synchronized on the selectorManager
     *
     * @param newTime
     */
    public void setTime(long newTime) {
        if (newTime < time) {
            logger.warn("Attempted to set time from " + time + " to " + newTime + ", ignoring.");
            return;
        }
        logger.debug("DirectTimeSource.setTime(" + time + "=>" + newTime + ")");
        time = newTime;
    }

    /**
     * Should be synchronized on the selectorManager
     */
    public void incrementTime(int millis) {
        setTime(time + millis);
    }

    public void sleep(long delay) throws InterruptedException {
        synchronized (selectorManager) { // to prevent an out of order acquisition
            // we only lock on the selector
            BlockingTimerTask btt = new BlockingTimerTask();
            pendingTimers.add(btt);
            logger.debug("DirectTimeSource.sleep(" + delay + ")");

            selectorManager.getTimer().schedule(btt, delay);

            while (!btt.done) {
                selectorManager.wait();
                if (btt.interrupted) throw new InterruptedException("TimeSource destroyed.");
            }
            pendingTimers.remove(btt);
        }
    }

    /**
     * TODO: Get the synchronization on this correct
     */
    public void destroy() {
        for (BlockingTimerTask btt : new ArrayList<>(pendingTimers)) {
            btt.interrupt();
        }
        pendingTimers.clear();
    }

//  public void wait(Object lock, int timeToWait) throws InterruptedException {
//    if (selector.isSelectorThread()) throw new IllegalStateException("You can't call this on the selector thread.");
//    
//    BlockingTimerTask2 btt = new BlockingTimerTask2(Thread.currentThread());
//    selectorManager.getTimer().schedule(btt,timeToWait);
//    try {
//      lock.wait();
//    } catch (InterruptedException ie) {
//      
//    }
//    btt.cancel();
//    
//  }

    private class BlockingTimerTask extends TimerTask {
        boolean done = false;
        boolean interrupted = false;

        public void run() {
            synchronized (selectorManager) {
                done = true;
                selectorManager.notifyAll();
                // selector already yields enough
//        Thread.yield();
            }
        }

        public void interrupt() {
            interrupted = true;
        }

    }

}
