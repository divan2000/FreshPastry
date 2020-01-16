package org.urajio.freshpastry.rice.p2p.util;

import org.urajio.freshpastry.rice.selector.Timer;
import org.urajio.freshpastry.rice.selector.TimerTask;

import java.util.WeakHashMap;

/**
 * Weak hash map that holds hard link to keys for a minimum time.
 *
 * @author Jeff Hoye
 */
public class TimerWeakHashMap<K, V> extends WeakHashMap<K, V> {

    int defaultDelay;
    Timer timer;

    public TimerWeakHashMap(Timer t, int delay) {
        this.defaultDelay = delay;
        timer = t;
    }

    public V put(K key, V val) {
        refresh(key);
        return super.put(key, val);
    }

    public void refresh(Object key) {
        refresh(key, defaultDelay);
    }

    public void refresh(Object key, int delay) {
        timer.schedule(
                new HardLinkTimerTask(key), delay);
    }

    public static class HardLinkTimerTask extends TimerTask {
        Object hardLink;

        public HardLinkTimerTask(Object hardLink) {
            this.hardLink = hardLink;
        }

        public void run() {
            // do nothing, just expire, taking the hard link away with you
        }
    }
}
