package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.rice.environment.random.RandomSource;

public interface ProximityGenerator {
    NodeRecord generateNodeRecord();

    void setRandom(RandomSource random);
}
