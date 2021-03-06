package ca.vijayan.flyweb.mdns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class PacketEncoder {
    ByteArrayOutputStream mStream;

    public PacketEncoder() {
        mStream = new ByteArrayOutputStream();
    }

    byte[] getBytes() {
        return mStream.toByteArray();
    }

    public void writeLabel(List<String> label) throws IOException {
        for (String part : label) {
            byte[] partBytes = part.getBytes("UTF-8");
            if (partBytes.length > 0x3f) {
                throw new IOException("DNS Record label is too long to encode.");
            }
            mStream.write(partBytes.length);
            mStream.write(partBytes);
        }
        mStream.write(0);
    }

    public void writeInt16(int i) {
        writeInt8(i >> 8);
        writeInt8(i);
    }

    public void writeInt8(int i) {
        mStream.write(i & 0xff);
    }
}
