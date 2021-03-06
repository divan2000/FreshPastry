package org.urajio.freshpastry.rice;

/**
 * Asynchronously executes a processing function, and returns the result.
 * Just like Runnable, but has a return value;
 *
 * @author Alan Mislove
 * @version $Id$
 */
public interface Executable<R, E extends Exception> {

    /**
     * Executes the potentially expensive task and returns the result.
     *
     * @return The result of the command.
     */
    R execute() throws E;

}
