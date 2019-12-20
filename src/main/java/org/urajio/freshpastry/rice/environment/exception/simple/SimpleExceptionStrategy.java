package org.urajio.freshpastry.rice.environment.exception.simple;

import org.urajio.freshpastry.rice.environment.exception.ExceptionStrategy;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import org.urajio.freshpastry.rice.selector.SelectorManager;

public class SimpleExceptionStrategy implements ExceptionStrategy {
  Logger logger;
  public SimpleExceptionStrategy(LogManager manager) {
    logger = manager.getLogger(SimpleExceptionStrategy.class, null); 
  }
  
  public void handleException(Object source, Throwable t) {
    if (logger.level <= Logger.WARNING) logger.logException("handleException("+source+")",t);
    if (source instanceof SelectorManager) {
      SelectorManager sm = (SelectorManager)source;
      sm.getEnvironment().destroy();
    }
  }

}
