package org.urajio.freshpastry.examples.transport.direct;

public interface Delivery {
    /**
     * What to do when time to deliver.
     */
    void deliver();

    /**
     * Preserve order.
     *
     * @return
     */
    int getSeq();
}
