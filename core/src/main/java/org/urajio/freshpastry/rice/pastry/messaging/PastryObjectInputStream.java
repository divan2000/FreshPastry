package org.urajio.freshpastry.rice.pastry.messaging;

import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/**
 * coalesces NodeHandles on the fly during java deserialization
 *
 * @author Jeff Hoye
 */
public class PastryObjectInputStream extends ObjectInputStream {

    protected PastryNode node;

    public PastryObjectInputStream(InputStream stream, PastryNode node)
            throws IOException {
        super(stream);
        this.node = node;
        enableResolveObject(true);
    }

    protected Object resolveObject(Object input) {
        if (input instanceof NodeHandle) {
//      System.out.println("POIS.resolveObject("+input+"@"+System.identityHashCode(input)+"):"+node);
            if (node != null) {
                // coalesce
                input = node.coalesce((NodeHandle) input);
            }
        }

        return input;
    }
}
