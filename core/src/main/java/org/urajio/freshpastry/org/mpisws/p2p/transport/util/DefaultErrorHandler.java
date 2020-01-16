package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.ErrorHandler;

import java.util.Map;

/**
 * Just logs the problems.
 *
 * @param <Identifier>
 * @author Jeff Hoye
 */
public class DefaultErrorHandler<Identifier> implements ErrorHandler<Identifier> {
    private final static Logger logger = LoggerFactory.getLogger(DefaultErrorHandler.class);

    public int NUM_BYTES_TO_PRINT = 8;

    @Override
    public void receivedUnexpectedData(Identifier id, byte[] bytes, int pos, Map<String, Object> options) {
        if (logger.isInfoEnabled()) {
            // make this pretty
            int numBytes = NUM_BYTES_TO_PRINT;
            if (bytes.length < numBytes) numBytes = bytes.length;
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < numBytes) {
                String s1 = bytes[i] + ",";
                sb.append(s1);
                i++;
            }
            logger.info(String.format("Unexpected data from %s %s", id, sb.toString()));
        }
    }

    @Override
    public void receivedException(Identifier i, Throwable error) {
        if (i == null) {
            logger.info(null, error);
        } else {
            logger.info(i.toString(), error);
        }
    }
}
