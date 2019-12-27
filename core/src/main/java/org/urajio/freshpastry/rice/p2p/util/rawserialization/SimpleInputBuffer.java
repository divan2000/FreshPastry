package org.urajio.freshpastry.rice.p2p.util.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.InputBuffer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SimpleInputBuffer extends DataInputStream implements InputBuffer {
    ByteArrayInputStream bais;

    public SimpleInputBuffer(ByteBuffer bb) {
        this(bb.array(), bb.position(), bb.remaining());
    }

    public SimpleInputBuffer(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public SimpleInputBuffer(byte[] bytes, int offset) {
        this(bytes, offset, bytes.length - offset);
    }

    public SimpleInputBuffer(byte[] bytes, int offset, int length) {
        super(new ByteArrayInputStream(bytes, offset, length));
        bais = (ByteArrayInputStream) this.in;
    }

//  public short peakShort() throws IOException {
//    bais.mark(2);
//    short temp = readShort();
//    bais.reset();
//    return temp;
//  }

    public int bytesRemaining() {
        try {
            return this.available();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return -1;
    }
}
