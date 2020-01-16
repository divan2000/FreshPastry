package org.urajio.freshpastry.org.mpisws.p2p.transport.priority;

import java.io.IOException;

public class QueueOverflowException extends IOException {
    Object identifier;
    Object message;
    Throwable cause;

    public QueueOverflowException(Object identifier, Object message) {
        super("Queue to " + identifier + " overflowed. Couldn't deliver message " + message);
        this.identifier = identifier;
        this.message = message;
    }

    public QueueOverflowException(Object identifier, Object message, Throwable cause) {
        this(identifier, message);
        this.cause = cause;
    }

    public Object getIdentifier() {
        return identifier;
    }

    public Object getAttemptedMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }
}
