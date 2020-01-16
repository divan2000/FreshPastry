package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.org.mpisws.p2p.transport.MessageRequestHandle;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.messaging.Message;

import java.util.Map;

public class PMessageReceiptImpl implements PMessageReceipt {
    MessageRequestHandle<NodeHandle, RawMessage> internal;
    Message message;
    Map<String, Object> options;
    boolean cancelled = false;

    public PMessageReceiptImpl(Message msg, Map<String, Object> options) {
        this.message = msg;
        this.options = options;
    }

    public NodeHandle getIdentifier() {
        if (internal == null) return null;
        return internal.getIdentifier();
    }

    public Message getMessage() {
        return message;
    }

    public Map<String, Object> getOptions() {
        return options;
//    return internal.getOptions();
    }

    /**
     * The synchronization code here must do the following:
     * <p>
     * cancel/setInternal can be called on any thread at any time
     * if both cancel and setInternal are called, then internal.cancel() is called the first time the second call is made.
     * cannot hold a lock while calling internal.cancel()
     * <p>
     * can cancel be called off of the selector?
     *
     * @return true if it has been cancelled for sure, false if it may/may-not be cancelled
     */
    public boolean cancel() {
        boolean callCancel = false;
        synchronized (this) {
            if (cancelled) return false;
            cancelled = true;
            if (internal != null) callCancel = true;
        }
        if (callCancel) return internal.cancel();
        return false;
    }

    public MessageRequestHandle<NodeHandle, RawMessage> getInternal() {
        return internal;
    }

    /**
     * See synchronization note on cancel()
     *
     * @param name
     */
    public void setInternal(MessageRequestHandle<NodeHandle, RawMessage> name) {
        boolean callCancel;
        synchronized (this) {
            if (internal != null && internal != name) {
                throw new RuntimeException("Internal already set old:" + internal + " new:" + name);
            }
            internal = name;
            callCancel = cancelled;
        }
        if (callCancel) internal.cancel();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String toString() {
        return "PMsgRecptI{" + message + "," + getIdentifier() + "}";
    }
}
