package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.util.TimerWeakHashMap;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * This impl allows responses up to timeout millis later.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public class TimeoutResponseStrategy<Identifier> implements ResponseStrategy<Identifier> {

    Map<Identifier, Long> lastTimeReceived;

    int timeout;

    TimeSource time;

    public TimeoutResponseStrategy(int timeout, Environment env) {
        this.timeout = timeout;
        this.lastTimeReceived = new TimerWeakHashMap<>(env.getSelectorManager(), 60000 + timeout);
        this.time = env.getTimeSource();
    }

    public void messageReceived(Identifier i, ByteBuffer msg, Map<String, Object> options) {
        lastTimeReceived.put(i, time.currentTimeMillis());
    }

    public void messageSent(Identifier i, ByteBuffer msg, Map<String, Object> options) {
        // TODO Auto-generated method stub

    }

    public boolean sendDirect(Identifier i, ByteBuffer msg, Map<String, Object> options) {
        long lastTime = 0;
        if (lastTimeReceived.containsKey(i)) {
            lastTime = lastTimeReceived.get(i);
        }
        return time.currentTimeMillis() <= (lastTime + timeout);
    }
}
