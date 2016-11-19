package ca.vijayan.flyweb.mdns;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * Created by Kannan Vijayan on 11/18/2016.
 */

public class DNSServiceInfo {
    List<String> mType;
    List<String> mName;
    Map<String, byte[]> mAttributes;
    InetAddress mAddress;
    int mPort;
    Key mKey;

    public DNSServiceInfo(List<String> type,
                          List<String> name,
                          Map<String, byte[]> attributes,
                          InetAddress address, int port)
    {
        mType = type;
        mName = name;
        mAttributes = attributes;
        mAddress = address;
        mPort = port;
        mKey = new Key(this);
    }

    public List<String> getType() {
        return mType;
    }
    public List<String> getName() {
        return mName;
    }
    public Map<String, byte[]> getAttributes() {
        return mAttributes;
    }
    public InetAddress getAddress() {
        return mAddress;
    }
    public int getPort() {
        return mPort;
    }
    public Key getKey() {
        return mKey;
    }

    public DNSServiceInfo clone() {
        return new DNSServiceInfo(mType, mName, mAttributes, mAddress, mPort);
    }

    public boolean isUpdated(DNSServiceInfo other) {
        assert(getKey().equals(other.getKey()));
        return !mAttributes.equals(other.mAttributes) ||
                !mAddress.equals(other.mAddress) ||
                (mPort != other.mPort);
    }
    public void updateWith(DNSServiceInfo other) {
        assert(getKey().equals(other.getKey()));
        mAttributes = other.mAttributes;
        mAddress = other.mAddress;
        mPort = other.mPort;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DNSServiceInfo { ");
        builder.append("type=").append(PacketParser.nameToDotted(mType)).append(", ");
        builder.append("name=").append(PacketParser.nameToDotted(mName)).append(", ");
        builder.append("addr=").append(mAddress.toString());
        builder.append(":").append(mPort).append("; ");
        boolean first = true;
        for (Map.Entry<String, byte[]> entry : mAttributes.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=>");
            try {
                String valStr = new String(entry.getValue(), "UTF-8");
                builder.append(valStr);
            } catch (Exception exc) {
                builder.append("???");
            }
        }
        builder.append(" }");

        return builder.toString();
    }

    public static class Key {
        DNSServiceInfo mServiceInfo;
        int mHashCode;

        Key(DNSServiceInfo serviceInfo) {
            mServiceInfo = serviceInfo;
            mHashCode = mServiceInfo.mType.hashCode() ^ mServiceInfo.mName.hashCode();
        }

        public DNSServiceInfo getServiceInfo() {
            return mServiceInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Key)) {
                return false;
            }

            Key other = (Key) obj;
            return mServiceInfo.mType.equals(other.mServiceInfo.mType) &&
                   mServiceInfo.mName.equals(other.mServiceInfo.mName);
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }
}
