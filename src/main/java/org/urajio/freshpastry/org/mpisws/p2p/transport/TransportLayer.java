package org.urajio.freshpastry.org.mpisws.p2p.transport;

import org.urajio.freshpastry.rice.Destructable;

import java.util.Map;

/**
 * The transport layer does provides the minimum functionality to provide communication
 * with flow control.<br><br>
 * 
 * The Identifier is the type of identifier this transport layer exports.  At the lowest level,
 * this is usually an InetSocketAddress, at the highest level, this is usually a NodeHandle.<br><br>
 * 
 * MessageType is the type of object this transport layer operates on.  At the lowest level, this is
 * usually a ByteBuffer, and at the highest level, a RawMessage.<br><br>
 * 
 * options are transport layer specific options that are passed through the transport layers.  If 
 * a layer finds an option in the map that it understands, it applies the option.  For example,
 * whether to send the message as UDP/TCP or encrypted or not.  Thus, if you specify an option,
 * but do not use a transport layer that handles the option, it will be ignored.<br><br>  
 * 
 * When a message is sent, or a socket is opened, each layer in the transport stack may add
 * a header.  For example, the magic number layer adds a special byte sequence to each outgoing
 * socket and message.<br><br>
 * 
 * When a message is read, or a socket is received, each layer will read only the header that it added.
 * For example, the magic number layer reads the bytes, to make sure that they match the expected byte 
 * sequence.  If they don't match, or a timeout occurs before sufficient bytes are read, the socket
 * is closed, or the message is discarded.<br><br>
 * 
 * Once the header has been read, the transport layer calls callback.incomingSocket() or 
 * callback.messageReceived().<br><br>
 * 
 * Sending a message and opening a socket may not be instant.  Since the message may be queued, you can include a 
 * continuation (deliverAckToMe/deliverSocketToMe) to be called back when the operation succeeds or fails.  It
 * will be called back with the same RequestHandle that was returned when the call was made.  Note that if 
 * the request fails immediately, the callback may be called before the method returns, thus you may not already
 * have a record of the RequestHandle.
 * 
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier> The type of node this layer operates on.
 * @param <MessageType> The type of message this layer sends.
 */
public interface TransportLayer<Identifier, MessageType> extends Destructable {
  /**
   * Open a socket to the Identifier
   * 
   * @param i who to open the socket to
   * @param deliverSocketToMe the callback when the socket is opened
   * @param options options on how to open the socket (don't source route, encrypt etc) (may not be respected if layer cannot provide service)
   * @return an object to cancel opening the socket if it takes to long, or is no longer relevant
   */
  SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options);
  
  /**
   * Send the message to the identifier
   * 
   * @param i the destination
   * @param m the message
   * @param options delivery options (don't source route, encrypt etc) (may not be respected if layer cannot provide service)
   * @param deliverAckToMe layer dependent notification when the message is sent (can indicate placed on the wire, point-to-point acknowledgment, or end-to-end acknowledgement)
   * @return ability to cancel the message if no longer relevant
   */
  MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Object> options);
  
  /**
   * The local node.
   * 
   * @return The local node.
   */
  Identifier getLocalIdentifier();
  
  /**
   * Toggle accepting new sockets.  Useful in flow control if overwhelmed by incoming sockets.
   * Default: true
   * 
   * @param b 
   */
  void acceptSockets(boolean b);
  
  /**
   * Toggle accepting incoming messages.  Useful in flow control if overwhelmed by incoming sockets.
   * Default: true
   * 
   * @param b 
   */
  void acceptMessages(boolean b);
  
  /**
   * Set the callback for incoming sockets/messages
   * @param callback the callback for incoming sockets/messages
   */
  void setCallback(TransportLayerCallback<Identifier, MessageType> callback);
  
  /**
   * To be notified of problems not related to an outgoing messaage/socket.  Or to be notified
   * if a callback isn't provided.
   * 
   * @param handler to be notified of problems not related to a specific messaage/socket.
   */
  void setErrorHandler(ErrorHandler<Identifier> handler);
}
