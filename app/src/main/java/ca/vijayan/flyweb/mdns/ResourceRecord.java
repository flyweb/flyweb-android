package ca.vijayan.flyweb.mdns;

import java.io.IOException;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class ResourceRecord extends Record {
    protected long mTTL;
    protected byte[] mData;
    protected ResourceData mResourceData;

    public ResourceRecord() {
        super();
    }

    public ResourceRecord(List<String> name, int recordType, int classCode, boolean cacheFlush, int ttl) {
        super(name, recordType, classCode, cacheFlush);
        mTTL = ttl;
    }

    long getTTL() {
        return mTTL;
    }

    public void parse(PacketParser parser) throws IOException {
        // Parse core resource fields.
        super.parse(parser);

        // Parse ttl.
        mTTL = parser.readInt32();
        int dataLength = parser.readInt16();
        mData = parser.readBytesExact(dataLength);

        PacketParser recordParser = new PacketParser(mData);
        switch (mRecordType) {
            case DNSPacket.RECORD_TYPE_A:
                ResourceData.A aRecord = new ResourceData.A();
                aRecord.parse(recordParser);
                mResourceData = aRecord;
            case DNSPacket.RECORD_TYPE_PTR:
                ResourceData.PTR ptrRecord = new ResourceData.PTR();
                ptrRecord.parse(recordParser, parser);
                mResourceData = ptrRecord;
            case DNSPacket.RECORD_TYPE_SRV:
                ResourceData.SRV srvRecord = new ResourceData.SRV();
                srvRecord.parse(recordParser, parser);
                mResourceData = srvRecord;
            case DNSPacket.RECORD_TYPE_TXT:
                ResourceData.TXT txtRecord = new ResourceData.TXT();
                txtRecord.parse(recordParser);
                mResourceData = txtRecord;
            default:
                throw new IOException("Unrecognized record type: " + mRecordType);
        }
    }

    public String toString() {
        return "RR{name=" + PacketParser.nameToDotted(mName) + ", rt=" + mRecordType +
                ", cc=" + mClassCode + ", ttl=" + mTTL + ", " + mResourceData.toString() + "}";
    }
}
