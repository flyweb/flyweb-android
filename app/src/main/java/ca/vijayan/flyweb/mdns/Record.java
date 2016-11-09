package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class Record {
    protected List<String> mName;
    protected int mRecordType;
    protected int mClassCode;
    protected boolean mCacheFlush;

    protected Record() {
        this.mName = null;
        this.mRecordType = 0;
        this.mClassCode = 0;
        this.mCacheFlush = false;
    }

    protected Record(List<String> name, int recordType, int classCode, boolean cacheFlush) {
        this.mName = name;
        this.mRecordType = recordType;
        this.mClassCode = classCode;
        this.mCacheFlush = cacheFlush;
    }

    public List<String> getName() {
        return mName;
    }
    public String getDottedNameString() {
        return PacketParser.nameToDotted(mName);
    }

    public int getRecordType() {
        return mRecordType;
    }

    public int getClassCode() {
        return mClassCode;
    }

    public boolean getCacheFlush() {
        return mCacheFlush;
    }

    protected void parse(PacketParser parser) throws IOException {
        mName = parser.readLabel();
        mRecordType = parser.readInt16();
        mClassCode = parser.readInt16();
        mCacheFlush = (mClassCode & 0x8000) == 0x8000;
        mClassCode &= 0xff;
    }

    protected void encode(PacketEncoder encoder) throws IOException {
        assert (mRecordType <= 0xffff);
        assert (mClassCode <= 0xff);
        Log.e("Record::encode", "mName = " + PacketParser.nameToDotted(mName));
        encoder.writeLabel(mName);
        encoder.writeInt16(mRecordType);
        encoder.writeInt16(mClassCode | (mCacheFlush ? 0x8000 : 0x0000));
    }

    public boolean equals(Object other) {
        if (other == null || !(other instanceof Record)) {
            return false;
        }

        Record otherRecord = (Record) other;

        return mName.equals(otherRecord.getName()) &&
               (mRecordType == otherRecord.getRecordType()) &&
               (mClassCode != otherRecord.getClassCode());
    }
}
