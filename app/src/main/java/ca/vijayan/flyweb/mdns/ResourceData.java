package ca.vijayan.flyweb.mdns;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kvijayan on 08/11/16.
 */

public abstract class ResourceData {
    protected ResourceData() {}

    abstract void writeToParcel(Parcel parcel, int i);

    public static class A extends ResourceData {
        byte[] mIp;

        public A() {
            mIp = null;
        }

        public A(byte[] ip) {
            assert (ip.length == 4);
            mIp = ip;
        }

        public byte[] getIp() {
            return mIp;
        }

        public void parse(PacketParser recordParser) throws IOException {
            mIp = recordParser.readBytesExact(4);
        }

        public String toString() {
            return "A[" + mIp[0] + "." + mIp[1] + "." + mIp[2] + "." + mIp[3] + "]";
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeByteArray(mIp);
        }
        public static A readFromParcel(Parcel parcel) {
            byte[] ip = new byte[4];
            parcel.readByteArray(ip);
            return new A(ip);
        }
    }

    public static class PTR extends ResourceData {
        List<String> mServiceName;

        public PTR() {
            mServiceName = null;
        }

        public PTR(List<String> serviceName) {
            mServiceName = serviceName;
        }

        public List<String> getServiceName() {
            return mServiceName;
        }
        public String getDottedServiceNameString() {
            return PacketParser.nameToDotted(mServiceName);
        }

        public void parse(PacketParser recordParser, PacketParser baseParser) throws IOException {
            mServiceName = recordParser.readLabel(baseParser);
        }

        public String toString() {
            return "PTR[" + PacketParser.nameToDotted(mServiceName) + "]";
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeStringList(mServiceName);
        }
        public static PTR readFromParcel(Parcel parcel) {
            List<String> serviceName = new ArrayList<>();
            parcel.readStringList(serviceName);
            return new PTR(serviceName);
        }
    }

    public static class SRV extends ResourceData {
        int mPriority;
        int mWeight;
        int mPort;
        List<String> mTarget;

        public SRV() {
            mPriority = 0;
            mWeight = 0;
            mPort = 0;
            mTarget = null;
        }

        public SRV(int priority, int weight, int port, List<String> target) {
            mPriority = priority;
            mWeight = weight;
            mPort = port;
            mTarget = target;
        }

        int getPriority() {
            return mPriority;
        }

        int getWeight() {
            return mWeight;
        }

        int getPort() {
            return mPort;
        }

        public List<String> getTarget() {
            return mTarget;
        }
        public String getDottedTargetString() {
            return PacketParser.nameToDotted(mTarget);
        }

        public void parse(PacketParser recordParser, PacketParser baseParser) throws IOException {
            mPriority = recordParser.readInt16();
            mWeight = recordParser.readInt16();
            mPort = recordParser.readInt16();
            mTarget = recordParser.readLabel(baseParser);
        }

        public String toString() {
            return "SRV[" + PacketParser.nameToDotted(mTarget) + ":" + mPort +
                    ", prio=" + mPriority + ", weight=" + mWeight + "]";
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeInt(mPriority);
            parcel.writeInt(mWeight);
            parcel.writeInt(mPort);
            parcel.writeStringList(mTarget);
        }
        public static SRV readFromParcel(Parcel parcel) {
            int priority = parcel.readInt();
            int weight = parcel.readInt();
            int port = parcel.readInt();
            List<String> target = new ArrayList<>();
            parcel.readStringList(target);
            return new SRV(priority, weight, port, target);
        }
    }

    public static class TXT extends ResourceData {
        List<byte[]> mRawEntries;
        Map<String, byte[]> mMap;

        public TXT() {
            mRawEntries = null;
            mMap = null;
        }

        public TXT(List<byte[]> rawEntries) {
            mRawEntries = rawEntries;
            generateMapFromRawEntries();
        }

        public TXT(Map<String, byte[]> entries) {
            ArrayList<byte[]> rawEntries = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ByteArrayOutputStream outs = new ByteArrayOutputStream();
                try {
                    outs.write(entry.getKey().getBytes("UTF-8"));
                    outs.write('=');
                    outs.write(entry.getValue());
                } catch (Exception exc) {
                    throw new RuntimeException(exc);
                }
            }
            mRawEntries = rawEntries;
            mMap = entries;
        }

        public List<byte[]> getRawEntries() {
            return mRawEntries;
        }
        public Map<String, byte[]> getMap() {
            return mMap;
        }

        public void parse(PacketParser recordParser) throws IOException {
            mRawEntries = new ArrayList<byte[]>();

            for (;;) {
                int len = recordParser.maybeReadInt8();
                if (len < 0) {
                    break;
                }

                assert (len <= 0xff);
                byte[] data = recordParser.readBytesExact(len);
                mRawEntries.add(data);
            }

            generateMapFromRawEntries();
        }

        void generateMapFromRawEntries() {
            mMap = new HashMap<>();
            for (byte[] data : mRawEntries) {
                // Find first occurrence of '='
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == '=') {
                        // Parse string.
                        byte[] firstPart = Arrays.copyOfRange(data, 0, i);
                        byte[] secondPart = Arrays.copyOfRange(data, i+1, data.length);
                        try {
                            String key = new String(firstPart, "UTF-8");
                            mMap.put(key, secondPart);
                        } catch (Exception exc) {
                            throw new RuntimeException("Unexpected unsupported encoding UTF-8", exc);
                        }
                    }
                }
            }
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("TXT[");
            boolean first = true;
            for (Map.Entry<String, byte[]> entry : mMap.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(entry.getKey());
                builder.append("=");
                builder.append(Arrays.toString(entry.getValue()));
            }
            builder.append("]");
            return builder.toString();
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeInt(mRawEntries.size());
            for (byte[] entry : mRawEntries) {
                parcel.writeInt(entry.length);
                parcel.writeByteArray(entry);
            }
        }
        public static TXT readFromParcel(Parcel parcel) {
            int numEntries = parcel.readInt();
            List<byte[]> rawEntries = new ArrayList<>();
            for (int i = 0; i < numEntries; i++) {
                int length = parcel.readInt();
                byte[] entry = new byte[length];
                parcel.readByteArray(entry);
                rawEntries.add(entry);
            }
            return new TXT(rawEntries);
        }
    }
}
