package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;

import java.util.Map;

public class MessageRequestHandleImpl<Identifier, MessageType> implements MessageRequestHandle<Identifier, MessageType> {
    Cancellable subCancellable;
    Identifier identifier;
    MessageType msg;
    Map<String, Object> options;

    public MessageRequestHandleImpl(Identifier i, MessageType m, Map<String, Object> options) {
        this.identifier = i;
        this.msg = m;
        this.options = options;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public MessageType getMessage() {
        return msg;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public boolean cancel() {
        if (subCancellable == null) return false;
        return subCancellable.cancel();
    }

    public Cancellable getSubCancellable() {
        return subCancellable;
    }

    public void setSubCancellable(Cancellable cancellable) {
        this.subCancellable = cancellable;
    }

    public String toString() {
        return "MRHi(" + identifier + "," + msg + "," + options + ")";
    }
}
