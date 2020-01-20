package org.urajio.freshpastry.examples.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeIdFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Constructs random node ids by SHA'ing consecutive numbers, with random
 * starting value.
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 * @version $Id$
 */
public class RandomNodeIdFactory implements NodeIdFactory {
    private final static Logger logger = LoggerFactory.getLogger(RandomNodeIdFactory.class);
    Environment environment;
    private long next;

    /**
     * Constructor.
     */
    public RandomNodeIdFactory(Environment env) {
        this.environment = env;
        next = env.getRandomSource().nextLong();
    }

    /**
     * generate a nodeId
     *
     * @return the new nodeId
     */
    public Id generateNodeId() {


        byte[] raw = new byte[8];
        long tmp = ++next;
        for (int i = 0; i < 8; i++) {
            raw[i] = (byte) (tmp & 0xff);
            tmp >>= 8;
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            logger.error("No SHA support!");
            throw new RuntimeException("No SHA support!", e);
        }

        md.update(raw);
        byte[] digest = md.digest();

        return Id.build(digest);
    }
}

