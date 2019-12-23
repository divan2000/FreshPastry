package org.urajio.freshpastry.rice.pastry.transport;

public interface PMessageNotification {

    void sendFailed(PMessageReceipt msg, Exception reason);

    void sent(PMessageReceipt msg);
}
