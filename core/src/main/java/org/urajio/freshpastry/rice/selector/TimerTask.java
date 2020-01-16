package org.urajio.freshpastry.rice.selector;

import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.CancellableTask;

/**
 * @author Jeff Hoye
 */
public abstract class TimerTask implements Comparable<TimerTask>, CancellableTask {
    protected boolean cancelled = false;
    protected int seq;
    protected SelectorManager selector;
    /**
     * If period is positive, task will be rescheduled.
     */
    protected int period = -1;
    protected boolean fixedRate = false;
    private long nextExecutionTime;

    public TimerTask() {

    }

    public TimerTask(long initialExecutionTime) {
        nextExecutionTime = initialExecutionTime;
    }

    public abstract void run();

    /**
     * Returns true if should re-insert.
     *
     * @return
     */
    public boolean execute(TimeSource ts) {
        if (cancelled) return false;
        run();
        // often cancelled in the execution
        if (cancelled) return false;
        if (period > 0) {
            if (fixedRate) {
                nextExecutionTime += period;
            } else {
                nextExecutionTime = ts.currentTimeMillis() + period;
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean cancel() {
        if (cancelled) {
            return false;
        }
        if (selector != null) {
            selector.removeTask(this);
        }
        cancelled = true;
        return true;
    }

    public long scheduledExecutionTime() {
        return nextExecutionTime;
    }

    public int compareTo(TimerTask arg0) {
        if (arg0 == this) return 0;
//    return (int)(tt.nextExecutionTime-nextExecutionTime);
        int diff = (int) (nextExecutionTime - arg0.nextExecutionTime);
        if (diff == 0) {
            // compare the sequence numbers
            diff = seq - arg0.seq;

            // if still same, try the hashcode
            if (diff == 0) {
                if (System.identityHashCode(this) < System.identityHashCode(arg0)) {
                    return 1;
                }
                return -1;
            }
        }
        return diff;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Makes the cancel operation remove the task from the queue as well.
     *
     * @param timerQueue that we are enqueued on
     */
    public void setSelectorManager(SelectorManager selector) {
        this.selector = selector;
    }

    protected void setNextExecutionTime(long l) {
        nextExecutionTime = l;
    }
}
