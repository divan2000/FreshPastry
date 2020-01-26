package org.urajio.freshpastry.examples.direct;

import org.urajio.freshpastry.examples.direct.proximitygenerators.EuclideanProximityGenerator;
import org.urajio.freshpastry.rice.environment.Environment;

/**
 * Euclidean network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed in a plane. Proximity is based on euclidean distance in the plane.
 *
 * @author Andrew Ladd
 * @author Rongmei Zhang
 * @version $Id$
 */
public class EuclideanNetwork<Identifier, MessageType> extends NetworkSimulatorImpl<Identifier, MessageType> {

    /**
     * Constructor.
     */
    public EuclideanNetwork(Environment env) {
        super(env, new EuclideanProximityGenerator(env.getParameters().getInt("pastry_direct_max_diameter")));
    }
}
