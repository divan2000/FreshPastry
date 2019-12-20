package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocket;
import org.urajio.freshpastry.org.mpisws.p2p.transport.P2PSocketReceiver;
import org.urajio.freshpastry.rice.environment.Environment;
import rice.environment.logging.Logger;
import org.urajio.freshpastry.rice.p2p.commonapi.appsocket.AppSocket;
import org.urajio.freshpastry.rice.p2p.commonapi.appsocket.AppSocketReceiver;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SocketAdapter<Identifier> implements AppSocket, P2PSocketReceiver<Identifier> {
  P2PSocket<Identifier> internal;
  Logger logger;
  Environment environment;
  
  public SocketAdapter(P2PSocket<Identifier> socket, Environment env) {
    this.internal = socket;
    this.logger = env.getLogManager().getLogger(SocketAdapter.class, null);
    this.environment = env;
  }

  public void close() {
    internal.close();
  }
  
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    long ret = 0;
    for (int i = offset; i < offset+length; i++) {
      ret += internal.read(dsts[i]);
    }
    return ret;
  }

  AppSocketReceiver reader = null;
  AppSocketReceiver writer = null;
  public void register(boolean wantToRead, boolean wantToWrite, int timeout, AppSocketReceiver receiver) {
    if (wantToRead) {
      if (reader != null && reader != receiver) throw new IllegalStateException("Already registered "+reader+" for reading. Can't register "+receiver);
      reader = receiver;
    }
    if (wantToWrite) {
      if (writer != null && writer != receiver) throw new IllegalStateException("Already registered "+reader+" for writing. Can't register "+receiver);
      writer = receiver;
    }
//    logger.log("register("+wantToRead+","+wantToWrite+","+receiver+")");
//    internal.register(wantToRead, wantToWrite, new AppSocketReceiverWrapper(receiver, this, environment));
    internal.register(wantToRead, wantToWrite, this);
  }

  public void receiveException(P2PSocket<Identifier> s, Exception e) {
    if (writer != null) {
      if (writer == reader) {
        AppSocketReceiver temp = writer;
        writer = null;
        reader = null;
        temp.receiveException(this, e);
      } else {
        AppSocketReceiver temp = writer;
        writer = null;
        temp.receiveException(this, e);
      }
    }
    
    if (reader != null) {
      AppSocketReceiver temp = reader;
      reader = null;
      temp.receiveException(this, e);
    }
  }

  public void receiveSelectResult(P2PSocket<Identifier> s,
      boolean canRead, boolean canWrite) throws IOException {
    if (logger.level <= Logger.FINEST) logger.log(this+"rsr("+internal+","+canRead+","+canWrite+")");
    if (canRead && canWrite && (reader == writer)) {      
      AppSocketReceiver temp = reader;
      reader = null;
      writer = null;
      temp.receiveSelectResult(this, canRead, canWrite);
      return;
    }

    if (canRead) {      
      AppSocketReceiver temp = reader;
      if (temp== null) {
        if (logger.level <= Logger.WARNING) logger.log("no reader in "+this+".rsr("+internal+","+canRead+","+canWrite+")");         
      } else {
        reader = null;
        temp.receiveSelectResult(this, true, false);
      }
    }

    if (canWrite) {      
      AppSocketReceiver temp = writer;
      if (temp == null) {
        if (logger.level <= Logger.WARNING) logger.log("no writer in "+this+".rsr("+internal+","+canRead+","+canWrite+")");        
      } else {
        writer = null;
        temp.receiveSelectResult(this, false, true);
      }
    }
  }

  public void shutdownOutput() {
    internal.shutdownOutput();    
  }

  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    long ret = 0;
    for (int i = offset; i < offset+length; i++) {
      ret += internal.write(srcs[i]);
    }
    return ret;
  }

  public long read(ByteBuffer dst) throws IOException {
    return internal.read(dst);
  }

  public long write(ByteBuffer src) throws IOException {
    return internal.write(src);
  }
  
  public String toString() {
    return "SA["+internal+"]";
  }
}
