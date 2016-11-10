package ca.vijayan.flyweb.mdns;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class PacketParser {
    byte[] mData;
    InputStream mStream;

    PacketParser(byte[] data) {
        mData = data;
        mStream = new ByteArrayInputStream(mData);
    }

    static public String nameToDotted(List<String> name) {
        assert (!name.isEmpty());

        StringBuilder builder = new StringBuilder();

        Iterator<String> iter = name.iterator();
        if (iter.hasNext()) {
            builder.append(iter.next());
            while (iter.hasNext()) {
                builder.append(".");
                builder.append(iter.next());
            }
        }
        return builder.toString();
    }

    InputStream inputStreamAt(int offset) {
        assert (offset <= mData.length);
        return new ByteArrayInputStream(mData, offset, mData.length - offset);
    }

    public byte[] readCharacterString() throws IOException {
        return readCharacterString(mStream);
    }
    public byte[] readCharacterString(InputStream ins) throws IOException {
        int len = readInt8(ins);
        return readBytesExact(ins, len);
    }

    public List<String> readLabel() throws IOException {
        return readLabel(mStream);
    }
    public List<String> readLabel(PacketParser basePacket) throws IOException {
        return readLabel(mStream, basePacket);
    }
    public List<String> readLabel(InputStream ins) throws IOException {
        return readLabel(ins, this);
    }
    public List<String> readLabel(InputStream ins, PacketParser basePacket) throws IOException {
        List<String> result = new ArrayList<>();
        readLabelInto(result, ins, basePacket);
        return result;
    }

    void readLabelInto(List<String> result, InputStream ins, PacketParser basePacket)
            throws IOException
    {
        for (;;) {
            int len = readInt8(ins);

            // Zero-length indicates end of label.
            if (len == 0) {
                break;
            }

            // Length < 0xc0 indicates immediate label.
            if (len < 0xc0) {
                // Parse len bytes into string piece.
                byte[] bytes = readBytesExact(ins, len);
                String s = new String(bytes, "UTF-8");
                result.add(s);
                continue;
            }

            // Otherwise, "compressed" label suffix reference.

            // Otherwise, read next byte.
            int offset = readInt8(ins);
            offset |= (len & 0x3f) << 8;

            // Read the label from the given offset.
            readLabelInto(result, basePacket.inputStreamAt(offset), basePacket);
            break;
        }
    }

    public byte[] readBytesExact(int nbytes) throws IOException {
        return readBytesExact(mStream, nbytes);
    }
    public byte[] readBytesExact(InputStream ins, int nbytes) throws IOException {
        byte[] bytes = new byte[nbytes];
        if (nbytes == 0) {
            return bytes;
        }
        int nread = ins.read(bytes);
        if (nread < nbytes) {
            throw new IOException("Premature end of DNS packet (nbytes=" + nbytes + ").");
        }
        return bytes;
    }

    public long readInt32() throws IOException {
        return readInt32(mStream);
    }
    public long readInt32(InputStream ins) throws IOException {
        long b0 = readInt8(ins);
        long b1 = readInt8(ins);
        long b2 = readInt8(ins);
        long b3 = readInt8(ins);
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public int readInt16() throws IOException {
        return readInt16(mStream);
    }
    public int readInt16(InputStream ins) throws IOException {
        int b0 = readInt8(ins);
        int b1 = readInt8(ins);
        return (b0 << 8) | b1;
    }

    public int readInt8() throws IOException {
        return readInt8(mStream);
    }
    public int readInt8(InputStream ins) throws IOException {
        int val = ins.read();
        if (val < 0) {
            throw new IOException("Premature end of DNS packet.");
        }
        assert (val <= 0xff);
        return val;
    }

    public int maybeReadInt8() throws IOException {
        return maybeReadInt8(mStream);
    }
    public int maybeReadInt8(InputStream ins) throws IOException {
        return ins.read();
    }
}
