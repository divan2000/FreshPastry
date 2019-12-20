package org.urajio.freshpastry.rice.p2p.commonapi.exception;

import java.io.IOException;

public class AppSocketException extends IOException {
  Throwable reason;

  public AppSocketException() {}
  
  public AppSocketException(Throwable reason) {
    this.reason = reason; 
  }

  public AppSocketException(String string) {
    super(string);
  }

  public Throwable reason() {
    return reason; 
  }
}
