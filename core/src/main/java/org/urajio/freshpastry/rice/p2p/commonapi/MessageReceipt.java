package org.urajio.freshpastry.rice.p2p.commonapi;

/**
 * Returned by a call to endpoint.route().
 * <p>
 * Can be used to cancel a message.
 *
 * @author Jeff Hoye
 */
public interface MessageReceipt extends Cancellable {
    Message getMessage();

    Id getId();

    NodeHandle getHint();
}
