package ca.vijayan.flyweb.mdns;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Kannan Vijayan on 11/18/2016.
 */

public class DNSServiceInfo implements Parcelable {
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

    public String displayName() {
        if (mName.isEmpty()) {
            return "<EMPTY>";
        } else {
            return mName.get(0);
        }
    }

    public String getBaseURL() {
        return "http://" + mAddress.getHostAddress() + ":" + mPort;
    }

    public String getServiceURL() {
        // Check for 'path' attribute.
        String baseUrl = getBaseURL();
        if (mAttributes.containsKey("path")) {
            try {
                String path = new String(mAttributes.get("path"), "UTF-8");
                baseUrl += path;
            } catch (UnsupportedEncodingException exc) {
                throw new RuntimeException("Unexpected UnsupportedEncodingException for UTF-8", exc);
            }
        }
        return baseUrl;
    }

    public DNSServiceInfo clone() {
        return new DNSServiceInfo(mType, mName, mAttributes, mAddress, mPort);
    }

    public static boolean attributesEqual(Map<String, byte[]> attr1,
                                          Map<String, byte[]> attr2)
    {
        if (!attr1.keySet().equals(attr2.keySet())) {
            return false;
        }
        for (String key : attr1.keySet()) {
            if (!Arrays.equals(attr1.get(key), attr2.get(key))) {
                return false;
            }
        }
        return true;
    }

    public boolean isUpdated(DNSServiceInfo other) {
        assert(getKey().equals(other.getKey()));
        return !attributesEqual(mAttributes, mAttributes) ||
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
        builder.append("type=").append(DNSPacket.nameToDotted(mType)).append(", ");
        builder.append("name=").append(DNSPacket.nameToDotted(mName)).append(", ");
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeStringList(mType);
        parcel.writeStringList(mName);
        parcel.writeInt(mAttributes.size());
        for (Map.Entry<String, byte[]> entry : mAttributes.entrySet()) {
            parcel.writeString(entry.getKey());
            parcel.writeInt(entry.getValue().length);
            parcel.writeByteArray(entry.getValue());
        }
        assert (mAddress instanceof Inet4Address);
        parcel.writeByteArray(((Inet4Address) mAddress).getAddress());
        parcel.writeInt(mPort);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public DNSServiceInfo createFromParcel(Parcel in) {
            List<String> type = new ArrayList<>();
            in.readStringList(type);

            List<String> name = new ArrayList<>();
            in.readStringList(name);

            Map<String, byte[]> attrs = new HashMap<>();
            int numAttrs = in.readInt();
            for (int i = 0; i < numAttrs; i++) {
                String attrname = in.readString();
                byte[] attrbytes = new byte[in.readInt()];
                in.readByteArray(attrbytes);
                attrs.put(attrname, attrbytes);
            }

            byte[] addrBytes = new byte[4];
            in.readByteArray(addrBytes);
            InetAddress addr;
            try {
                addr = Inet4Address.getByAddress(addrBytes);
            } catch (UnknownHostException exc) {
                throw new RuntimeException("Unexpected UNKNOWN HOST Exception.", exc);
            }

            int port = in.readInt();

            return new DNSServiceInfo(type, name, attrs, addr, port);
        }

        public DNSServiceInfo[] newArray(int size) {
            return new DNSServiceInfo[size];
        }
    };

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

        @Override
        public String toString() {
            return "DNSServiceInfo.Key{" + mServiceInfo.getType().toString() + ":" +
                    mServiceInfo.getName().toString() + "}";
        }
    }
}
