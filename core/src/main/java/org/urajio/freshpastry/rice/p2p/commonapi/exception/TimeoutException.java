package org.urajio.freshpastry.rice.p2p.commonapi.exception;

public class TimeoutException extends AppSocketException {

    public TimeoutException() {
        super();
    }

    public TimeoutException(String string) {
        super(string);
    }

    public TimeoutException(Throwable reason) {
        super(reason);
    }
}
