package org.urajio.freshpastry.rice.pastry.transport;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.pastry.Id;
import org.urajio.freshpastry.rice.pastry.messaging.Message;
import org.urajio.freshpastry.rice.pastry.socket.TransportLayerNodeHandle;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class BogusNodeHandle extends TransportLayerNodeHandle<Collection<InetSocketAddress>> {
    public Collection<InetSocketAddress> addresses;

    public BogusNodeHandle(InetSocketAddress address) {
        addresses = Collections.singletonList(address);
    }

    public BogusNodeHandle(InetSocketAddress[] bootstraps) {
        addresses = Arrays.asList(bootstraps);
    }

    @Override
    public boolean equals(Object obj) {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public int getLiveness() {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public Id getNodeId() {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public String toString() {
        return "BogusNodeHandle " + addresses;
    }

    @Override
    public int hashCode() {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public boolean ping() {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public int proximity() {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public void receiveMessage(Message msg) {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public void serialize(OutputBuffer buf) {
        throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
    }

    @Override
    public Collection<InetSocketAddress> getAddress() {
        return addresses;
    }

    @Override
    public long getEpoch() {
        return 0;
    }

}
