package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.rice.selector.TimerTask;

public class DirectTimerTask extends TimerTask {

    MessageDelivery md;

    DirectTimerTask(MessageDelivery md, long nextExecutionTime, int period, boolean fixed) {
        this.md = md;
        setNextExecutionTime(nextExecutionTime);
        this.period = period;
        this.fixedRate = fixed;
    }

    DirectTimerTask(MessageDelivery md, long nextExecutionTime, int period) {
        this(md, nextExecutionTime, period, false);
    }

    DirectTimerTask(MessageDelivery md, long nextExecutionTime) {
        this(md, nextExecutionTime, -1, false);
    }

    public void run() {
        md.deliver();
    }

    public String toString() {
        return "DirectTT for " + md.msg + " to " + md.node;
    }

}
