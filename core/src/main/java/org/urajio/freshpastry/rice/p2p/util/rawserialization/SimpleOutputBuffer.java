package org.urajio.freshpastry.rice.p2p.util.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SimpleOutputBuffer extends DataOutputStream implements OutputBuffer {
    ByteArrayOutputStream baos;

    public SimpleOutputBuffer(int size) {
        super(new ByteArrayOutputStream(size));
        baos = (ByteArrayOutputStream) out;
    }

    public SimpleOutputBuffer() {
        super(new ByteArrayOutputStream());
        baos = (ByteArrayOutputStream) out;
    }

    public void writeByte(byte v) throws IOException {
        this.write(v);
    }

    public void writeChar(char v) throws IOException {
        writeChar((int) v);
    }

    public void writeShort(short v) throws IOException {
        writeShort((int) v);
    }

    public int bytesRemaining() {
        return Integer.MAX_VALUE;
    }

    public byte[] getBytes() {
        return baos.toByteArray();
    }

    public ByteBuffer getByteBuffer() {
        return ByteBuffer.wrap(getBytes());
    }

    /**
     * The amount of bytes written so far... the size (not the capacity).
     *
     * @return
     */
    public int getWritten() {
        return written;
    }
}
