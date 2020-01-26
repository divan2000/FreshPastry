package org.urajio.freshpastry.examples.transport.direct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.environment.time.simulated.DirectTimeSource;
import org.urajio.freshpastry.rice.selector.SelectorManager;

public class EventSimulator {
    private final static Logger logger = LoggerFactory.getLogger(EventSimulator.class);
    protected final SelectorManager manager;
    protected Environment environment;
    protected RandomSource random;
    protected TimeSource timeSource;
    // System Time is the system clock
    // Sim Time is the simulated clock
    long maxSpeedRequestSystemTime = 0;
    long maxSpeedRequestSimTime = 0;
    float maxSpeed = 0.0f;
    /**
     * This is a guardian for printing the "Invalid TimeSource" warning.  So that it is only printed once.
     */
    boolean printedDirectTimeSourceWarning = false;
    boolean running = false; // Invariant: only modified on the selector
    // true if we are responsible for incrementing the time
    private boolean isDirectTimeSource = false;

    public EventSimulator(Environment env, RandomSource random) {
        this.environment = env;
        this.random = random;
        manager = environment.getSelectorManager();

        timeSource = env.getTimeSource();
        if (timeSource instanceof DirectTimeSource) {
            isDirectTimeSource = true;
        }
        manager.setSelect(false);
    }

    public void setMaxSpeed(float speed) {
        if (!isDirectTimeSource) {
            if (!printedDirectTimeSourceWarning) {
                logger.warn("Invalid TimeSource for setMaxSpeed()/setFullSpeed().  Use Environment.directEnvironment() to construct your Environment.");
                printedDirectTimeSourceWarning = true;
            }
        }
        maxSpeedRequestSystemTime = System.currentTimeMillis();
        maxSpeedRequestSimTime = timeSource.currentTimeMillis();
        maxSpeed = speed;
    }

    public void setFullSpeed() {
        setMaxSpeed(-1.0f);
    }

    /**
     * Delivers 1 message. Will advance the clock if necessary.
     * <p>
     * If there is a message in the queue, deliver that and return true. If there
     * is a message in the taskQueue, update the clock if necessary, deliver that,
     * then return true. If both are empty, return false;
     */
    protected boolean simulate() throws InterruptedException {
        if (!isDirectTimeSource) {
            return true;
        }
        if (!environment.getSelectorManager().isSelectorThread()) {
            throw new RuntimeException("Must be on selector thread");
        }
        synchronized (manager) { // so we can wait on it, and so the clock and nextExecution don't change

            long scheduledExecutionTime = manager.getNextTaskExecutionTime();
            if (scheduledExecutionTime < 0) {
                logger.debug("taskQueue is empty");
                return false;
            }

            if (scheduledExecutionTime > timeSource.currentTimeMillis()) {
                long newSimTime = scheduledExecutionTime;
                if (maxSpeed > 0) {
                    long sysTime = System.currentTimeMillis();
                    long sysTimeDiff = sysTime - maxSpeedRequestSystemTime;

                    long maxSimTime = (long) (maxSpeedRequestSimTime + (sysTimeDiff * maxSpeed));

                    if (maxSimTime < newSimTime) {
                        // we need to throttle
                        long neededSysDelay = (long) ((newSimTime - maxSimTime) / maxSpeed);
                        if (neededSysDelay >= 1) {
                            manager.wait(neededSysDelay);
                            long now = System.currentTimeMillis();
                            long delay = now - sysTime;
                            if (delay < neededSysDelay) return true;
                        }
                    }
                }

                logger.debug("the time is now " + newSimTime);
                ((DirectTimeSource) timeSource).setTime(newSimTime);
            }
        } // synchronized(manager)
        return true;
    }

    public void start() {
        // this makes things single threaded
        manager.invoke(new Runnable() {
            public void run() {
                if (running) {
                    return;
                }
                running = true;
                manager.invoke(new Runnable() {
                    public void run() {
                        if (!running) {
                            return;
                        }
                        try {
                            if (!simulate()) {
                                synchronized (manager) {
                                    try {
                                        manager.wait(100); // must wait on the real clock, because the simulated clock can only be advanced by simulate()
                                    } catch (InterruptedException ie) {
                                        logger.error("BasicNetworkSimulator interrupted.", ie);
                                    }
                                }
                            }
                            // re-invoke the simulation task
                            manager.invoke(this);
                        } catch (InterruptedException ie) {
                            logger.error("BasicNetworkSimulator.start()", ie);
                            stop();
                        }
                    }
                });
            }
        });
    }

    public void stop() {
        manager.invoke(new Runnable() {
            public void run() {
                running = false;
            }
        });
    }
}
