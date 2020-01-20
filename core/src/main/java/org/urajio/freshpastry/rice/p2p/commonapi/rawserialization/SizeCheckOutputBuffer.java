package org.urajio.freshpastry.rice.p2p.commonapi.rawserialization;

public interface SizeCheckOutputBuffer extends OutputBuffer {
    void writeSpecial(Object o);

  int bytesWritten();
}
