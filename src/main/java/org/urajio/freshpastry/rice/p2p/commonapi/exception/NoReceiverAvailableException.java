package org.urajio.freshpastry.rice.p2p.commonapi.exception;

/**
 * Raised if there is no acceptor socket available.
 * 
 * @author Jeff Hoye
 */
public class NoReceiverAvailableException extends AppSocketException {
  public NoReceiverAvailableException() {
    super();
  }
}
