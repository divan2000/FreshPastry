package org.urajio.freshpastry.rice.pastry;

public interface ReadyStrategy {
    boolean isReady();

    void setReady(boolean r);

    /**
     * Called when it is time to take over as the renderstrategy.
     */
    void start();

    void stop();
}
