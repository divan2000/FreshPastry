package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.SocketRequestHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.util.Map;

public class SocketRequestHandleImpl<Identifier> implements SocketRequestHandle<Identifier> {
  private static final Logger logger = LoggerFactory.getLogger(SocketRequestHandleImpl.class);
  Identifier identifier;
  Map<String, Object> options;
  Cancellable subCancellable;

//  protected boolean cancelled = false;
  
  public SocketRequestHandleImpl(Identifier i, Map<String, Object> options) {
    this.identifier = i;
    this.options = options;
    if (logger == null) throw new IllegalArgumentException("logger is null");
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  public boolean cancel() {
//    if (cancelled) return true;
//    cancelled = true;
    if (subCancellable != null) {
//      try {
        return subCancellable.cancel();
//      } catch (IllegalStateException ise) {
//        if (logger.level <= Logger.WARNING) logger.log(ise.toString());
//        throw ise;
//      }
    }
    return false;
  }

  public void setSubCancellable(Cancellable sub) {
//    if (sub == null) throw new IllegalArgumentException("sub must be non-null");
//    if (cancelled) sub.cancel();
    this.subCancellable = sub;
  }

  public Cancellable getSubCancellable() {
    return subCancellable;
  }

  @Override
  public String toString() {
    return "SRHi{"+identifier+","+options+"}";
  }
}
