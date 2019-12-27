package org.urajio.freshpastry.rice.environment.exception;

public interface ExceptionStrategy {

    void handleException(Object source, Throwable t);

}
