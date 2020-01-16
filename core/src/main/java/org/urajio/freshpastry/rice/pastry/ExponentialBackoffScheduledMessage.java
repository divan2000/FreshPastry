package org.urajio.freshpastry.rice.pastry;


import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.selector.Timer;
import org.urajio.freshpastry.rice.selector.TimerTask;

/**
 * @author jeffh
 * <p>
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ExponentialBackoffScheduledMessage extends ScheduledMessage {
    boolean cancelled = false;
    EBTimerTask myTask;
    Timer timer;
    long initialPeriod;
    double expBase;
    int numTimes = 0;
    long lastTime = 0;
    long maxTime;

    /**
     * @param node
     * @param msg
     * @param initialPeriod
     * @param expBase
     */
    public ExponentialBackoffScheduledMessage(PastryNode node, Message msg, Timer timer, long initialDelay, long initialPeriod, double expBase, long maxPeriod) {
        super(node, msg);
        this.timer = timer;
        this.initialPeriod = initialPeriod;
        this.expBase = expBase;
        this.maxTime = maxPeriod;
        schedule(initialDelay);
    }

    public ExponentialBackoffScheduledMessage(PastryNode node, Message msg, Timer timer, long initialDelay, double expBase) {
        this(node, msg, timer, initialDelay, initialDelay, expBase, -1);
//    super(node,msg);
//    this.timer = timer;
//    this.initialPeriod = initialDelay;
//    this.expBase = expBase;
//    schedule(initialDelay);
        numTimes = 1;
    }


    private void schedule(long time) {
        myTask = new EBTimerTask();
        timer.schedule(myTask, time);
    }

    public boolean cancel() {
        super.cancel();
        if (myTask != null) {
            myTask.cancel();
            myTask = null;
        }
        boolean temp = cancelled;
        cancelled = true;
        return temp;
    }

    public void run() {
        if (!cancelled) {
            if (myTask != null) {
                lastTime = myTask.scheduledExecutionTime();
            }
            super.run();
            long time = (long) (initialPeriod * Math.pow(expBase, numTimes));
            if (maxTime >= 0) {
                time = Math.min(time, maxTime);
            }
            schedule(time);
            numTimes++;
        }
    }

    public long scheduledExecutionTime() {
        return lastTime;
    }

    class EBTimerTask extends TimerTask {
        public void run() {
            ExponentialBackoffScheduledMessage.this.run();
        }
    }
}
