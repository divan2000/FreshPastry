package org.urajio.freshpastry.rice.environment.random.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.random.RandomSource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * Simple, insecure random source
 *
 * @author Jeff Hoye
 */
public class SimpleRandomSource implements RandomSource {
    private final static Logger logger = LoggerFactory.getLogger(SimpleRandomSource.class);

    private final Random rnd;

    public SimpleRandomSource(long seed) {
        logger.info("Custom RNG seed = " + seed);
        rnd = new Random(seed);
    }

    public SimpleRandomSource() {
        byte[] bytes = new SecureRandom().generateSeed(8);

        // clever byte[] to long
        long seed = 0;
        seed += (long) (bytes[7] & 0x000000FF) << 56;
        seed += (long) (bytes[6] & 0x000000FF) << 48;
        seed += (long) (bytes[5] & 0x000000FF) << 40;
        seed += (long) (bytes[4] & 0x000000FF) << 32;
        seed += (bytes[3] & 0x000000FF) << 24;
        seed += (bytes[2] & 0x000000FF) << 16;
        seed += (bytes[1] & 0x000000FF) << 8;
        seed += (bytes[0] & 0x000000FF);

        logger.info("RNG seed = " + seed);
        rnd = new Random(seed);
    }

    public boolean nextBoolean() {
        boolean ret = rnd.nextBoolean();
        logger.debug("nextBoolean = " + ret);
        return ret;
    }

    public void nextBytes(byte[] bytes) {
        rnd.nextBytes(bytes);
        logger.debug("nextBytes[" + bytes.length + "] = " + Arrays.toString(bytes));
    }

    public double nextDouble() {
        double ret = rnd.nextDouble();
        logger.debug("nextDouble = " + ret);
        return ret;
    }

    public float nextFloat() {
        float ret = rnd.nextFloat();
        logger.debug("nextFloat = " + ret);
        return ret;
    }

    public double nextGaussian() {
        double ret = rnd.nextGaussian();
        logger.debug("nextGaussian = " + ret);
        return ret;
    }

    public int nextInt() {
        int ret = rnd.nextInt();
        logger.debug("nextInt = " + ret);
        return ret;
    }

    public int nextInt(int max) {
        int ret = rnd.nextInt(max);
        logger.debug("nextInt2 = " + ret);
        return ret;
    }

    public long nextLong() {
        long ret = rnd.nextLong();
        logger.debug("nextLong = " + ret);
        return ret;
    }
}
