package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.NodeIdFactory;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Constructs NodeIds for virtual nodes derived from the IP address and port
 * number of this Java VM.
 *
 * @author Peter Druschel
 * @version $Id$
 */

public class IPNodeIdFactory implements NodeIdFactory {
    private final static Logger logger = LoggerFactory.getLogger(IPNodeIdFactory.class);

    private static int nextInstance = 0;
    Environment environment;
    private InetAddress localIP;
    private int port;

    /**
     * Constructor.
     *
     * @param port the port number on which this Java VM listens
     */

    public IPNodeIdFactory(InetAddress localIP, int port, Environment env) {
        this.port = port;
        this.environment = env;
        try {
            this.localIP = localIP;
            if (localIP.isLoopbackAddress()) {
                throw new Exception("got loopback address: nodeIds will not be unique across computers!");
            }
        } catch (Exception e) {
            logger.error("ALERT: IPNodeIdFactory cannot determine local IP address", e);
        }
    }

    /**
     * generate a nodeId multiple invocations result in a deterministic series of
     * randomized NodeIds, seeded by the IP address of the local host.
     *
     * @return the new nodeId
     */
    @Override
    public Id generateNodeId() {
        byte[] rawIP = localIP.getAddress();

        byte[] rawPort = new byte[2];
        int tmp = port;
        for (int i = 0; i < 2; i++) {
            rawPort[i] = (byte) (tmp & 0xff);
            tmp >>= 8;
        }

        byte[] raw = new byte[4];
        tmp = ++nextInstance;
        for (int i = 0; i < 4; i++) {
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

        md.update(rawIP);
        md.update(rawPort);
        md.update(raw);
        byte[] digest = md.digest();

        return Id.build(digest);
    }
}

