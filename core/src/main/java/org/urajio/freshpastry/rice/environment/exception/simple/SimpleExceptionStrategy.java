package org.urajio.freshpastry.rice.environment.exception.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.exception.ExceptionStrategy;
import org.urajio.freshpastry.rice.selector.SelectorManager;

public class SimpleExceptionStrategy implements ExceptionStrategy {
    private final static Logger logger = LoggerFactory.getLogger(SimpleExceptionStrategy.class);

    public void handleException(Object source, Throwable t) {
        logger.warn("handleException(" + source + ")", t);
        if (source instanceof SelectorManager) {
            SelectorManager sm = (SelectorManager) source;
            sm.getEnvironment().destroy();
        }
    }

}
