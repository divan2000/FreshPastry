package org.urajio.freshpastry.org.mpisws.p2p.transport.commonapi;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

import java.util.Map;

public interface OptionsAdder {

    /**
     * Add any options related to this message
     *
     * @param options
     * @param m
     * @return
     */
    Map<String, Object> addOptions(Map<String, Object> options, RawMessage m);

}
