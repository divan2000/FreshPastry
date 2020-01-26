package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.examples.direct.proximitygenerators.SphereNetworkProximityGenerator;
import org.urajio.freshpastry.rice.environment.Environment;

/**
 * Sphere network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed on a sphere. Proximity is based on euclidean distance on the sphere.
 *
 * @author Y. Charlie Hu
 * @author Rongmei Zhang
 * @version $Id$
 */
public class SphereNetwork<Identifier, MessageType> extends NetworkSimulatorImpl<Identifier, MessageType> {

    /**
     * Constructor.
     */
    public SphereNetwork(Environment env) {
        super(env, new SphereNetworkProximityGenerator(env.getParameters().getInt("pastry_direct_max_diameter")));
    }
}

