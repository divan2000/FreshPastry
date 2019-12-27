package org.urajio.freshpastry.rice.environment.random;

/**
 * Provides a virtualized random interface for FreePastry.
 * <p>
 * Usually acquired by calling environment.getRandomSource().
 *
 * @author Jeff Hoye
 */
public interface RandomSource {
    boolean nextBoolean();
    void nextBytes(byte[] bytes);
    double nextDouble();
    float nextFloat();
    double nextGaussian();
    int nextInt();
    int nextInt(int max);
    long nextLong();
}
