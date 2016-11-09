package ca.vijayan.flyweb.mdns;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kvijayan on 07/11/16.
 */

public class QuestionRecord extends Record {
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

    public boolean equals(Object other) {
        if (other == null || !(other instanceof QuestionRecord)) {
            return false;
        }
        return super.equals(other);
    }

    public String toString() {
        return "QR{name=" + PacketParser.nameToDotted(mName) + ", rt=" + mRecordType +
                ", cc=" + mClassCode + "}";
    }
}
