package org.urajio.freshpastry.rice.environment.processing.simple;

import org.urajio.freshpastry.rice.environment.processing.WorkRequest;

/**
 * @author Jeff Hoye
 */
public class BlockingIOThread extends Thread {
    WorkQueue workQ;

    volatile boolean running = true;

    public BlockingIOThread(WorkQueue workQ) {
        super("Persistence Worker Thread");
        this.workQ = workQ;
    }

    public void run() {
        running = true;
        while (running) {
            WorkRequest wr = workQ.dequeue();
            if (wr != null)
                wr.run();
        }
    }

    @SuppressWarnings("deprecation")
    public void destroy() {
        running = false;
        workQ.destroy();
    }
}