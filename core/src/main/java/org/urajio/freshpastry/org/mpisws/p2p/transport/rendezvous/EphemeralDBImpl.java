package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.p2p.util.tuples.MutableTuple;

import java.util.HashMap;
import java.util.Map;

public class EphemeralDBImpl<Identifier, HighIdentifier> implements EphemeralDB<Identifier, HighIdentifier> {
    private final static Logger logger = LoggerFactory.getLogger(EphemeralDBImpl.class);

    /**
     * Time where a NAT will have reset the port to a new forwarding
     * <p>
     * in millis
     * <p>
     * Default 2 hours
     */
    protected long STALE_PORT_TIME = 2 * 60 * 60 * 1000;
    protected long nextTag = NO_TAG + 1;
    protected Map<HighIdentifier, Long> highToTag = new HashMap<>();
    protected Map<Identifier, Long> ephemeralToTag = new HashMap<>();
    /**
     * maps tag to ephemeral and timestamp
     */
    protected Map<Long, MutableTuple<Identifier, Long>> tagToEphemeral = new HashMap<>();
    TimeSource time;

    /**
     * @param env
     * @param stalePortTime how long until we should forget about the port binding
     */
    public EphemeralDBImpl(Environment env, long stalePortTime) {
        time = env.getTimeSource();
        this.STALE_PORT_TIME = stalePortTime;
    }


    /**
     * Tell the DB that the high identifier points to this tag
     *
     * @param high
     * @param tag
     */
    public void mapHighToTag(HighIdentifier high, long tag) {
        logger.debug("mapHighToTag(" + high + "," + tag + ")");
        highToTag.put(high, tag);
    }

    public Identifier getEphemeral(HighIdentifier high) {
        Long tag = highToTag.get(high);
        if (tag == null) {
            logger.debug("getEphemeral(" + high + "):null");
            return null;
        }
        Identifier ret = getEphemeral(tag, null);
        if (ret == null) {
            highToTag.remove(high);
        }
        logger.debug("getEphemeral(" + high + "):" + ret);
        return ret;
    }

    public Identifier getEphemeral(long tag, Identifier i) {
        MutableTuple<Identifier, Long> ret = tagToEphemeral.get(tag);
        if (ret == null) {
            logger.debug("getEphemeral(" + tag + "," + i + "):" + i);
            return i;
        }
        if (ret.b() < time.currentTimeMillis() - STALE_PORT_TIME) {
            clear(tag);
            logger.debug("getEphemeral(" + tag + "," + i + "):" + i);
            return i;
        }
        logger.debug("getEphemeral(" + tag + "," + i + "):" + ret.a());
        return ret.a();
    }

    public long getTagForEphemeral(Identifier addr) {
        long now = time.currentTimeMillis();
        Long tag = ephemeralToTag.get(addr);
        if (tag != null) {
            MutableTuple<Identifier, Long> ret = tagToEphemeral.get(tag);
            if (ret.b() < now - STALE_PORT_TIME) {
                // it's stale
                clear(tag);
            } else {
                // update timestamp
                ret.setB(now);
                logger.debug("getTagForEphemeral(" + addr + "):" + tag);
                return tag;
            }
        }

        // if we're here, we need to create a new one
        tag = nextTag++;
        MutableTuple<Identifier, Long> ret = new MutableTuple<>(addr, now);
        ephemeralToTag.put(addr, tag);
        tagToEphemeral.put(tag, ret);
        logger.debug("getTagForEphemeral(" + addr + "):" + tag);
        return tag;
    }

    protected void clear(long tag) {
        logger.debug("clear(" + tag + ")");
        MutableTuple<Identifier, Long> ret = tagToEphemeral.remove(tag);
        ephemeralToTag.remove(ret.a());
    }
}
