package org.urajio.freshpastry.examples.direct.proximitygenerators;

import org.urajio.freshpastry.examples.direct.NodeRecord;
import org.urajio.freshpastry.examples.direct.ProximityGenerator;
import org.urajio.freshpastry.rice.environment.random.RandomSource;

public class EuclideanProximityGenerator implements ProximityGenerator {
    int side;
    RandomSource random;

    /**
     * Constructor.
     */
    public EuclideanProximityGenerator(int maxDiameter) {
        side = (int) (maxDiameter / Math.sqrt(2.0));
    }

    public NodeRecord generateNodeRecord() {
        return new EuclideanNodeRecord();
    }

    public void setRandom(RandomSource random) {
        this.random = random;
    }

    /**
     * Initialize a random Euclidean NodeRecord
     *
     * @author amislove
     * @version $Id: EuclideanNetwork.java 3613 2007-02-15 14:45:14Z jstewart $
     */
    private class EuclideanNodeRecord implements NodeRecord {
        /**
         * The euclidean position.
         */
        public int x, y;

        public boolean alive;

        public EuclideanNodeRecord() {
            x = random.nextInt() % side;
            y = random.nextInt() % side;

            alive = true;
        }

        public float proximity(NodeRecord that) {
            return Math.round((networkDelay(that) * 2.0));
        }

        public float networkDelay(NodeRecord that) {
            EuclideanNodeRecord nr = (EuclideanNodeRecord) that;
            int dx = x - nr.x;
            int dy = y - nr.y;

            float ret = (float) Math.sqrt(dx * dx + dy * dy);
            if ((ret < 2.0) && !this.equals(that)) return (float) 2.0;

            return ret;
        }

        public String toString() {
            return "ENR(" + x + "," + y + ")";
        }

        public void markDead() {
        }
    }
}
