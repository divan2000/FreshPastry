package org.urajio.freshpastry.rice.pastry;

import java.net.InetSocketAddress;

/**
 * Represents a listener to pastry network activity.  This interface will notify
 * you of opening/closing sockets, and all network traffic.
 * <p>
 * <p/>You hear about opening/closing sockets via <code>channelOpened()</code>/<code>channelClosed()</code> methods.
 * <p>
 * <p/>You hear about network input via <code>dataReceived()</code> and network output via
 * <code>dataSent()</code>.
 * <p>
 * <p/>channelOpened() has a reason for opening the chanel.  The reason falls under
 * matrix of reasons:
 * <ul>
 *  <li>It may have been initiated locally, or be accepting of the socket from a
 *   remote node.  Reasons who's name has ACC in them are accepted, all of the
 *   rest are initiated locally.</li>
 *
 *  <li>It may be for a Normal connection, a source route (SR), a bootstrap (which
 * just requests internal state from FreePastry) or for an application level socket.</li>
 * </ul>
 * <p>
 * <p/>There are currently the following limitations for NetworkListener regarding
 * Application Sockets which may be fixed later:
 * <ol>
 *   <li>when accepting an application socket the reason is REASON_ACC_NORMAL</li>
 *   <li>you are not notified about data to/from these sockets, only that they
 *   have been opened.</li>
 * </ol>
 * <p>
 * <p/>the <code>dataSent()</code>/<code>dataReceived()</code> methods include a wireType.  These tell you if the
 * data was TCP/UDP and wether it was part of the SourceRoute
 *
 * @author Jeff Hoye
 * @version $Id$
 */
public interface NetworkListener {

    /**
     * TCP traffic (not source-routed)
     */
    int TYPE_TCP = 0x00;
    /**
     * UDP traffic (not source-routed)
     */
    int TYPE_UDP = 0x01;
    /**
     * TCP traffic source-routed (you are an intermediate hop, not the source nor destination)
     */
    int TYPE_SR_TCP = 0x10;
    /**
     * UDP traffic source-routed (you are an intermediate hop, not the source nor destination)
     */
    int TYPE_SR_UDP = 0x11;

    /**
     * Local node opened a socket for normal FreePastry traffic.
     */
    int REASON_NORMAL = 0;
    /**
     * Local node opened a socket for source routing.
     */
    int REASON_SR = 1;
    /**
     * Local node opened a to acquire bootstrap information.
     */
    int REASON_BOOTSTRAP = 2;

    /**
     * Remote node opened a socket for normal FreePastry traffic.
     */
    int REASON_ACC_NORMAL = 3;
    /**
     * Remote node opened a socket for source routing.
     */
    int REASON_ACC_SR = 4;
    /**
     * Remote node opened a to acquire bootstrap information.
     */
    int REASON_ACC_BOOTSTRAP = 5;
    /**
     * Local node opened an application level socket.
     */
    int REASON_APP_SOCKET_NORMAL = 6;

    /**
     * Called when a socket is opened.
     *
     * @param addr   the address the socket was opened to
     * @param reason see above
     */
    void channelOpened(InetSocketAddress addr, int reason);

    /**
     * Called when a socket is closed.
     *
     * @param addr the address the socket was opened to
     */
    void channelClosed(InetSocketAddress addr);

    /**
     * called when data is sent.
     *
     * @param msgAddress    the application the message belongs to
     * @param msgType       the type of message for that application
     * @param socketAddress the socket it is from
     * @param size          the size of the message
     * @param wireType      UDP/TCP
     */
    void dataSent(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);

    /**
     * called when data is received.
     *
     * @param msgAddress    the application the message belongs to
     * @param msgType       the type of message for that application
     * @param socketAddress the socket it is from
     * @param size          the size of the message
     * @param wireType      UDP/TCP
     */
    void dataReceived(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);

}


