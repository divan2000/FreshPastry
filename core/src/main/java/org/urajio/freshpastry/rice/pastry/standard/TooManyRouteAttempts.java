package org.urajio.freshpastry.rice.pastry.standard;

import org.urajio.freshpastry.rice.pastry.routing.RouteMessage;

import java.io.IOException;

public class TooManyRouteAttempts extends IOException {

    public TooManyRouteAttempts(RouteMessage rm, int max_retries) {
        super("Too many attempts to route the message " + rm);
    }
}
