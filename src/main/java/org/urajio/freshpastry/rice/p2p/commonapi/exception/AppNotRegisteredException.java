package org.urajio.freshpastry.rice.p2p.commonapi.exception;

/**
 * The application has not been registered on that node.
 * 
 * @author Jeff Hoye
 */
public class AppNotRegisteredException extends AppSocketException {
  private int appid;

  public AppNotRegisteredException(int appid) {
    super("Application with id "+appid+" not registered.");
    this.appid = appid;
  }

  public int getAppid() {
    return appid;
  }
}
