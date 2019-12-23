package org.urajio.freshpastry.rice.environment.processing.simple;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Jeff Hoye
 */
public class ProcessingThread extends Thread {

    PriorityBlockingQueue<ProcessingRequest> queue;

    volatile boolean running = false;

    public ProcessingThread(String name,
                            PriorityBlockingQueue<ProcessingRequest> queue) {
        super(name);
        this.queue = queue;
    }

    public void run() {
        running = true;
        while (running) {
            try {
                ProcessingRequest e = queue.take();
                if (e != null)
                    e.run();
            } catch (java.lang.InterruptedException ie) {
                // do nothing (maybe should call errorHandler)
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void destroy() {
        running = false;
        interrupt();
    }
}
