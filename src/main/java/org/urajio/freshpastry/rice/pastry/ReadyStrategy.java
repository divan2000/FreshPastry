package org.urajio.freshpastry.rice.pastry;

public interface ReadyStrategy {
  void setReady(boolean r);
  boolean isReady();
  /**
   * Called when it is time to take over as the renderstrategy.
   *
   */
  void start();
  void stop();
}
