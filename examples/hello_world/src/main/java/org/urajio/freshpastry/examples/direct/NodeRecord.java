package org.urajio.freshpastry.examples.direct;

public interface NodeRecord {

    float networkDelay(NodeRecord nrb);

    float proximity(NodeRecord nrb);

    void markDead();
}
