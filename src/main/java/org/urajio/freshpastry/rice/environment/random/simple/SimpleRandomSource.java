package org.urajio.freshpastry.rice.environment.random.simple;

import java.net.InetAddress;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.random.RandomSource;

/**
 * @author Jeff Hoye
 */
public class SimpleRandomSource implements RandomSource {
  private final static Logger logger = LoggerFactory.getLogger(SimpleRandomSource.class);

  Random rnd;
  

  String instance;
  
  public SimpleRandomSource(long seed, String instance) {
    init(seed, instance);
  }
    
  public SimpleRandomSource(long seed) {
    this(seed, null);
  }
    
  public SimpleRandomSource() {
    this(null);
  }
  
  public SimpleRandomSource(String instance) {
      // NOTE: Since we are often starting up a bunch of nodes on planetlab
      // at the same time, we need this randomsource to be seeded by more
      // than just the clock, we will include the IP address
      // as amazing as this sounds, it happened in a network of 20 on 7/19/2005
      // also, if you think about it, I was starting all of the nodes at the same 
      // instant, and they had synchronized clocks, if they all started within 1/10th of
      // a second, then there is only 100 different numbers to seed the generator with
      // -Jeff
      long time = System.currentTimeMillis();
      try {
        byte[] foo = InetAddress.getLocalHost().getAddress();
        for (int ctr = 0; ctr < foo.length; ctr++) {
          int i = (int)foo[ctr];
          i <<= (ctr*8);
          time ^= i; 
        }
      } catch (Exception e) {
        // if there is no NIC, screw it, this is really unlikely anyway  
      }
      init(time, instance);
  }
  
  public void setLogManager() {
    // TODO: dsdiv remove this method
  }
  
  private void init(long seed, String instance) {
    logger.info("RNG seed = "+seed);
    rnd = new Random(seed);
  }
  
  public boolean nextBoolean() {
    boolean ret = rnd.nextBoolean();
    logger.debug("nextBoolean = "+ret);
    return ret;
  }
  
  public void nextBytes(byte[] bytes) {
    rnd.nextBytes(bytes);
    logger.debug("nextBytes["+bytes.length+"] = "+bytes);
  }
  
  public double nextDouble() {
    double ret = rnd.nextDouble();
    logger.debug("nextDouble = "+ret);
    return ret;
  }
  
  public float nextFloat() {
    float ret = rnd.nextFloat();
    logger.debug("nextFloat = "+ret);
    return ret;
  }
  
  public double nextGaussian() {
    double ret = rnd.nextGaussian();
    logger.debug("nextGaussian = "+ret);
    return ret;
  }
  
  public int nextInt() {
    int ret = rnd.nextInt();
    logger.debug("nextInt = "+ret);
    return ret;
  }
  
  public int nextInt(int max) {
    int ret = rnd.nextInt(max);
    logger.debug("nextInt2 = "+ret);
    return ret;
  }
  
  public long nextLong() {
    long ret = rnd.nextLong();
    logger.debug("nextLong = "+ret);
    return ret;
  }
}
