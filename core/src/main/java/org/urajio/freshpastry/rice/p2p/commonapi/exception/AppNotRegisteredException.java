package org.urajio.freshpastry.rice.p2p.commonapi.exception;

/**
 * The application has not been registered on that node.
 *
 * @author Jeff Hoye
 */
public class AppNotRegisteredException extends AppSocketException {
    private int appId;

    public AppNotRegisteredException(int appId) {
        super("Application with id " + appId + " not registered.");
        this.appId = appId;
    }

    public int getAppId() {
        return appId;
    }
}
