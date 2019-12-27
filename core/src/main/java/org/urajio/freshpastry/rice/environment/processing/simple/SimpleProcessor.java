package org.urajio.freshpastry.rice.environment.processing.simple;

import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.Executable;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.processing.Processor;
import org.urajio.freshpastry.rice.environment.processing.WorkRequest;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.selector.SelectorManager;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Jeff Hoye
 */
public class SimpleProcessor implements Processor {
    // the queue used for processing requests
    protected PriorityBlockingQueue<ProcessingRequest> QUEUE;
    protected ProcessingThread THREAD;

    // for blocking IO WorkRequests
    protected WorkQueue workQueue;
    protected BlockingIOThread bioThread;

    long seq = Long.MIN_VALUE;

    public SimpleProcessor(String name) {
        QUEUE = new PriorityBlockingQueue<>();
        THREAD = new ProcessingThread(name + ".ProcessingThread", QUEUE);
        THREAD.start();
        THREAD.setPriority(Thread.MIN_PRIORITY);
        workQueue = new WorkQueue();
        bioThread = new BlockingIOThread(workQueue);
        bioThread.start();
    }

    /**
     * This is a test to make sure the order is correct.
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Environment env = new Environment();
        Processor p = env.getProcessor();
        // block the processor for 1 second while we schedule some more stuff
        p.process(new Executable() {
            public Object execute() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
                return null;
            }

        }, new Continuation() {

            public void receiveResult(Object result) {
                System.out.println("Done blocking.");
            }

            public void receiveException(Exception exception) {
                exception.printStackTrace();
            }

        }, env.getSelectorManager(), env.getTimeSource());

        for (int seq = 0; seq < 10; seq++) {
            final int mySeq = seq;
            p.process(new Executable() {
                public Object execute() {
                    System.out.println("Executed Seq: " + mySeq);
                    return null;
                }

            }, new Continuation() {
                public void receiveResult(Object result) {
                    System.out.println("Received Seq: " + mySeq);
                }

                public void receiveException(Exception exception) {
                    exception.printStackTrace();
                }

            }, env.getSelectorManager(), env.getTimeSource());
            System.out.println("Done scheduling " + mySeq);
        }
    }

    /**
     * Schedules a job for processing on the dedicated processing thread. CPU
     * intensive jobs, such as encryption, erasure encoding, or bloom filter
     * creation should never be done in the context of the underlying node's
     * thread, and should only be done via this method.
     *
     * @param task    The task to run on the processing thread
     * @param command The command to return the result to once it's done
     */
    public <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command,
                                                        SelectorManager selector, TimeSource ts) {
        return process(task, command, 0, selector, ts);
    }

    public <R, E extends Exception> Cancellable process(Executable<R, E> task, Continuation<R, E> command, int priority,
                                                        SelectorManager selector, TimeSource ts) {
        long nextSeq;
        synchronized (SimpleProcessor.this) {
            nextSeq = seq++;
        }
        ProcessingRequest ret = new ProcessingRequest(task, command, priority, nextSeq, ts, selector);
        QUEUE.offer(ret);
        return ret;
    }

    public Cancellable processBlockingIO(WorkRequest workRequest) {
        workQueue.enqueue(workRequest);
        return workRequest;
    }

    public Queue<ProcessingRequest> getQueue() {
        return QUEUE;
    }

    public void destroy() {
        THREAD.destroy();
        QUEUE.clear();
        bioThread.destroy();
        workQueue.destroy();
    }

    public WorkQueue getIOQueue() {
        return workQueue;
    }
}
