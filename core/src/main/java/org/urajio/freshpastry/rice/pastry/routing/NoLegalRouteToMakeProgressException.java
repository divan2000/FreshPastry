package org.urajio.freshpastry.rice.pastry.routing;

import org.urajio.freshpastry.rice.pastry.Id;

import java.io.IOException;

public class NoLegalRouteToMakeProgressException extends IOException {

    protected Id target;

    public NoLegalRouteToMakeProgressException(Id target) {
        super("No legal route to the target " + target);
        this.target = target;
    }

    public Id getTarget() {
        return target;
    }
}
