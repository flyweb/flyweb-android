package ca.vijayan.flyweb.mdns;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by kvijayan on 08/11/16.
 */

public class Record implements Parcelable {
    protected List<String> mName;
    protected int mRecordType;
    protected int mClassCode;
    protected boolean mCacheFlush;
    protected int mHashCode;

    protected Record() {
        this.mName = null;
        this.mRecordType = 0;
        this.mClassCode = 0;
        this.mCacheFlush = false;
        this.mHashCode = 0;
    }

    protected Record(List<String> name, int recordType, int classCode, boolean cacheFlush) {
        this.mName = name;
        this.mRecordType = recordType;
        this.mClassCode = classCode;
        this.mCacheFlush = cacheFlush;
        this.mHashCode = 0;
        calculateHashCode();
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
        calculateHashCode();
    }

    protected void encode(PacketEncoder encoder) throws IOException {
        assert (mRecordType <= 0xffff);
        assert (mClassCode <= 0xff);
        Log.e("Record::encode", "mName = " + PacketParser.nameToDotted(mName));
        encoder.writeLabel(mName);
        encoder.writeInt16(mRecordType);
        encoder.writeInt16(mClassCode | (mCacheFlush ? 0x8000 : 0x0000));
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof Record)) {
            return false;
        }

        Record otherRecord = (Record) other;

        return mName.equals(otherRecord.getName()) &&
               (mRecordType == otherRecord.getRecordType()) &&
               (mClassCode != otherRecord.getClassCode());
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    private void calculateHashCode() {
        ByteArrayOutputStream outs = new ByteArrayOutputStream();
        try {
            outs.write(PacketParser.nameToDotted(mName).getBytes("UTF-8"));
            outs.write((mRecordType >> 8) & 0xff);
            outs.write(mRecordType & 0xff);
            outs.write((mClassCode >> 8) & 0xff);
            outs.write(mClassCode & 0xff);
        } catch (Exception exc) {
            throw new RuntimeException("Unexpected error calculating hash code", exc);
        }
        mHashCode = Arrays.hashCode(outs.toByteArray());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeStringList(mName);
        parcel.writeInt(mRecordType);
        parcel.writeInt(mClassCode);
        parcel.writeByte((byte) (mCacheFlush ? 1 : 0));
    }
}
