package ca.vijayan.flyweb.mdns;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kvijayan on 07/11/16.
 */

public class QuestionRecord extends Record implements Parcelable {
    public QuestionRecord() {
        super();
    }

    public QuestionRecord(List<String> name, int recordType, int classCode, boolean cacheFlush) {
        super(name, recordType, classCode, cacheFlush);
    }

    public void parse(PacketParser parser) throws IOException {
        super.parse(parser);
    }

    public void encode(PacketEncoder encoder) throws IOException {
        super.encode(encoder);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof QuestionRecord)) {
            return false;
        }
        return super.equals(other);
    }

    @Override
    public String toString() {
        return "QR{name=" + PacketParser.nameToDotted(mName) + ", rt=" + mRecordType +
                ", cc=" + mClassCode + "}";
    }

    public static final Parcelable.Creator<QuestionRecord> CREATOR =
            new Parcelable.Creator<QuestionRecord>() {
                @Override
                public QuestionRecord createFromParcel(Parcel parcel) {
                    List<String> name = new ArrayList<String>();
                    parcel.readStringList(name);
                    int recordType = parcel.readInt();
                    int classCode = parcel.readInt();
                    boolean cacheFlush = parcel.readByte() == 0 ? false : true;
                    return new QuestionRecord(name, recordType, classCode, cacheFlush);
                }

                @Override
                public QuestionRecord[] newArray(int i) {
                    return new QuestionRecord[i];
                }
            };
}
