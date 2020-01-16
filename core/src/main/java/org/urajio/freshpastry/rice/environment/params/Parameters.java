package org.urajio.freshpastry.rice.environment.params;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Parameters interface for FreePastry
 * <p>
 * Usually acquired by calling environment.getParameters().
 *
 * @author Jeff Hoye
 */
public interface Parameters {
    /**
     * Remove the key
     *
     * @param name
     */
    void remove(String name);

    /**
     * See if the parameters contains the key
     *
     * @param name
     * @return
     */
    boolean contains(String name);

    /**
     * Persistently stores the parameters.
     *
     * @throws IOException
     */
    void store() throws IOException;

    // getters
    String getString(String paramName);

    String[] getStringArray(String paramName);

    int getInt(String paramName);

    double getDouble(String paramName);

    float getFloat(String paramName);

    long getLong(String paramName);

    boolean getBoolean(String paramName);

    /**
     * String format is dnsname
     * ex: "computer.school.edu"
     *
     * @param paramName
     * @return
     * @throws UnknownHostException
     */
    InetAddress getInetAddress(String paramName) throws UnknownHostException;


    /**
     * String format is name:port
     * ex: "computer.school.edu:1984"
     *
     * @param paramName
     * @return
     */
    InetSocketAddress getInetSocketAddress(String paramName) throws UnknownHostException;

    /**
     * String format is comma seperated.
     * ex: "computer.school.edu:1984,computer2.school.edu:1984,computer.school.edu:1985"
     *
     * @param paramName
     * @return
     */
    InetSocketAddress[] getInetSocketAddressArray(String paramName) throws UnknownHostException;

    // setters
    void setString(String paramName, String val);

    void setStringArray(String paramName, String[] val);

    void setInt(String paramName, int val);

    void setDouble(String paramName, double val);

    void setFloat(String paramName, float val);

    void setLong(String paramName, long val);

    void setBoolean(String paramName, boolean val);

    void setInetAddress(String paramName, InetAddress val);

    void setInetSocketAddress(String paramName, InetSocketAddress val);

    void setInetSocketAddressArray(String paramName, InetSocketAddress[] val);

    void restoreDefault(String paramName);

    void addChangeListener(ParameterChangeListener p);

    void removeChangeListener(ParameterChangeListener p);
}
