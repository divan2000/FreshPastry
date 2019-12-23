package org.urajio.freshpastry.rice.pastry;

public class JoinFailedException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 7582828712730559215L;

    public JoinFailedException() {
        super();
    }

    public JoinFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public JoinFailedException(String message) {
        super(message);
    }

    public JoinFailedException(Throwable cause) {
        super(cause);
    }

}
