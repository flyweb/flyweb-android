package ca.vijayan.flyweb.mdns;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class ResourceRecord extends Record implements Parcelable {
    protected long mTTL;
    protected ResourceData mResourceData;

    public ResourceRecord() {
        super();
    }

    public ResourceRecord(List<String> name, int recordType, int classCode, boolean cacheFlush,
                          int ttl, ResourceData resourceData)
    {
        super(name, recordType, classCode, cacheFlush);
        mTTL = ttl;
        mResourceData = resourceData;
    }

    long getTTL() {
        return mTTL;
    }
    void setTTL(long ttl) {
        mTTL = ttl;
    }

    ResourceData getResourceData() {
        return mResourceData;
    }

    public void parse(PacketParser parser) throws IOException {
        // Parse core resource fields.
        super.parse(parser);

        // Parse ttl.
        mTTL = parser.readInt32();
        int dataLength = parser.readInt16();
        byte[] data = parser.readBytesExact(dataLength);

        PacketParser recordParser = new PacketParser(data);
        switch (mRecordType) {
            case DNSPacket.RECORD_TYPE_A:
                ResourceData.A aRecord = new ResourceData.A();
                aRecord.parse(recordParser);
                mResourceData = aRecord;
                break;
            case DNSPacket.RECORD_TYPE_PTR:
                ResourceData.PTR ptrRecord = new ResourceData.PTR();
                ptrRecord.parse(recordParser, parser);
                mResourceData = ptrRecord;
                break;
            case DNSPacket.RECORD_TYPE_SRV:
                ResourceData.SRV srvRecord = new ResourceData.SRV();
                srvRecord.parse(recordParser, parser);
                mResourceData = srvRecord;
                break;
            case DNSPacket.RECORD_TYPE_TXT:
                ResourceData.TXT txtRecord = new ResourceData.TXT();
                txtRecord.parse(recordParser);
                mResourceData = txtRecord;
                break;
            default:
                mResourceData = null;
                break;
        }
    }

    public String toString() {
        return "RR{name=" + PacketParser.nameToDotted(mName) + ", rt=" + mRecordType +
                ", cc=" + mClassCode + ", ttl=" + mTTL +
                (mResourceData == null ? "" : ", " + mResourceData.toString()) +
                "}";
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeLong(mTTL);
        if (mResourceData != null) {
            mResourceData.writeToParcel(parcel, i);
        }
    }

    public static final Parcelable.Creator<ResourceRecord> CREATOR =
            new Parcelable.Creator<ResourceRecord>() {
                @Override
                public ResourceRecord createFromParcel(Parcel parcel) {
                    List<String> name = new ArrayList<String>();
                    parcel.readStringList(name);
                    int recordType = parcel.readInt();
                    int classCode = parcel.readInt();
                    boolean cacheFlush = parcel.readByte() == 0 ? false : true;
                    int ttl = parcel.readInt();
                    ResourceData resourceData = null;
                    if (recordType == DNSPacket.RECORD_TYPE_A) {
                        resourceData = ResourceData.A.readFromParcel(parcel);
                    } else if (recordType == DNSPacket.RECORD_TYPE_PTR) {
                        resourceData = ResourceData.PTR.readFromParcel(parcel);
                    } else if (recordType == DNSPacket.RECORD_TYPE_SRV) {
                        resourceData = ResourceData.SRV.readFromParcel(parcel);
                    } else if (recordType == DNSPacket.RECORD_TYPE_TXT) {
                        resourceData = ResourceData.TXT.readFromParcel(parcel);
                    }
                    return new ResourceRecord(name, recordType, classCode, cacheFlush, ttl, resourceData);
                }

                @Override
                public ResourceRecord[] newArray(int i) {
                    return new ResourceRecord[i];
                }
            };
}
