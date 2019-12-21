package org.urajio.freshpastry.rice.selector;


/**
 * @author Jeff Hoye
 */
public interface Timer {
  TimerTask scheduleAtFixedRate(TimerTask task, long delay, long period);
  TimerTask schedule(TimerTask task, long delay);
  TimerTask schedule(TimerTask task, long delay, long period);
  TimerTask schedule(TimerTask dtt);
}
