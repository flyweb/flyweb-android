package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kvijayan on 06/11/16.
 */

public class DNSPacket {

    /**
     * DNS Packet Structure
     * *************************************************
     *
     * Header
     * ======
     *
     * 00                   2-Bytes                   15
     * -------------------------------------------------
     * |00|01|02|03|04|05|06|07|08|09|10|11|12|13|14|15|
     * -------------------------------------------------
     * |<==================== ID =====================>|
     * |QR|<== OP ===>|AA|TC|RD|RA|UN|AD|CD|<== RC ===>|
     * |<================== QDCOUNT ==================>|
     * |<================== ANCOUNT ==================>|
     * |<================== NSCOUNT ==================>|
     * |<================== ARCOUNT ==================>|
     * -------------------------------------------------
     *
     * ID:        2-Bytes
     * FLAGS:     2-Bytes
     *  - QR:     1-Bit
     *  - OP:     4-Bits
     *  - AA:     1-Bit
     *  - TC:     1-Bit
     *  - RD:     1-Bit
     *  - RA:     1-Bit
     *  - UN:     1-Bit
     *  - AD:     1-Bit
     *  - CD:     1-Bit
     *  - RC:     4-Bits
     * QDCOUNT:   2-Bytes
     * ANCOUNT:   2-Bytes
     * NSCOUNT:   2-Bytes
     * ARCOUNT:   2-Bytes
     *
     *
     * Data
     * ====
     *
     * 00                   2-Bytes                   15
     * -------------------------------------------------
     * |00|01|02|03|04|05|06|07|08|09|10|11|12|13|14|15|
     * -------------------------------------------------
     * |<???=============== QD[...] ===============???>|
     * |<???=============== AN[...] ===============???>|
     * |<???=============== NS[...] ===============???>|
     * |<???=============== AR[...] ===============???>|
     * -------------------------------------------------
     *
     * QD:        ??-Bytes
     * AN:        ??-Bytes
     * NS:        ??-Bytes
     * AR:        ??-Bytes
     *
     *
     * Question Record
     * ===============
     *
     * 00                   2-Bytes                   15
     * -------------------------------------------------
     * |00|01|02|03|04|05|06|07|08|09|10|11|12|13|14|15|
     * -------------------------------------------------
     * |<???================ NAME =================???>|
     * |<=================== TYPE ====================>|
     * |<=================== CLASS ===================>|
     * -------------------------------------------------
     *
     * NAME:      ??-Bytes
     * TYPE:      2-Bytes
     * CLASS:     2-Bytes
     *
     *
     * Resource Record
     * ===============
     *
     * 00                   4-Bytes                   31
     * -------------------------------------------------
     * |00|02|04|06|08|10|12|14|16|18|20|22|24|26|28|30|
     * -------------------------------------------------
     * |<???================ NAME =================???>|
     * |<======= TYPE ========>|<======= CLASS =======>|
     * |<==================== TTL ====================>|
     * |<====== DATALEN ======>|<???==== DATA =====???>|
     * -------------------------------------------------
     *
     * NAME:      ??-Bytes
     * TYPE:      2-Bytes
     * CLASS:     2-Bytes
     * DATALEN:   2-Bytes
     * DATA:      ??-Bytes (Specified By DATALEN)
     */

    // Query response codes.
    public static final boolean QR_CODE_QUERY = false;
    public static final boolean QR_CODE_RESPONSE = true;

    // Authoritative answer codes.
    public static final boolean AA_CODE_NO = false;
    public static final boolean AA_CODE_YES = true;

    public static final int CLASS_CODE_IN = 1; // Internet
    public static final int CLASS_CODE_CS = 2; // CSNET
    public static final int CLASS_CODE_CH = 3; // CHAOS
    public static final int CLASS_CODE_HS = 4; // Hesiod
    public static final int CLASS_CODE_NONE = 0xfe;
    public static final int CLASS_CODE_ANY = 0xff;

    public static final int RECORD_TYPE_SIGZERO = 0;        // RFC 2931
    public static final int RECORD_TYPE_A = 1;              // RFC 1035
    public static final int RECORD_TYPE_NS = 2;             // RFC 1035
    public static final int RECORD_TYPE_MD = 3;             // RFC 1035
    public static final int RECORD_TYPE_MF = 4;             // RFC 1035
    public static final int RECORD_TYPE_CNAME = 5;          // RFC 1035
    public static final int RECORD_TYPE_SOA = 6;            // RFC 1035
    public static final int RECORD_TYPE_MB = 7;             // RFC 1035
    public static final int RECORD_TYPE_MG = 8;             // RFC 1035
    public static final int RECORD_TYPE_MR = 9;             // RFC 1035
    public static final int RECORD_TYPE_NULL = 10;          // RFC 1035
    public static final int RECORD_TYPE_WKS = 11;           // RFC 1035
    public static final int RECORD_TYPE_PTR = 12;           // RFC 1035
    public static final int RECORD_TYPE_HINFO = 13;         // RFC 1035
    public static final int RECORD_TYPE_MINFO = 14;         // RFC 1035
    public static final int RECORD_TYPE_MX = 15;            // RFC 1035
    public static final int RECORD_TYPE_TXT = 16;           // RFC 1035
    public static final int RECORD_TYPE_RP = 17;            // RFC 1183
    public static final int RECORD_TYPE_AFSDB = 18;         // RFC 1183
    public static final int RECORD_TYPE_X25 = 19;           // RFC 1183
    public static final int RECORD_TYPE_ISDN = 20;          // RFC 1183
    public static final int RECORD_TYPE_RT = 21;            // RFC 1183
    public static final int RECORD_TYPE_NSAP = 22;          // RFC 1706
    public static final int RECORD_TYPE_NSAP_PTR = 23;      // RFC 1348
    public static final int RECORD_TYPE_SIG = 24;           // RFC 2535
    public static final int RECORD_TYPE_KEY = 25;           // RFC 2535
    public static final int RECORD_TYPE_PX = 26;            // RFC 2163
    public static final int RECORD_TYPE_GPOS = 27;          // RFC 1712
    public static final int RECORD_TYPE_AAAA = 28;          // RFC 1886
    public static final int RECORD_TYPE_LOC = 29;           // RFC 1876
    public static final int RECORD_TYPE_NXT = 30;           // RFC 2535
    public static final int RECORD_TYPE_EID = 31;           // RFC ????
    public static final int RECORD_TYPE_NIMLOC = 32;        // RFC ????
    public static final int RECORD_TYPE_SRV = 33;           // RFC 2052
    public static final int RECORD_TYPE_ATMA = 34;          // RFC ????
    public static final int RECORD_TYPE_NAPTR = 35;         // RFC 2168
    public static final int RECORD_TYPE_KX = 36;            // RFC 2230
    public static final int RECORD_TYPE_CERT = 37;          // RFC 2538
    public static final int RECORD_TYPE_DNAME = 39;         // RFC 2672
    public static final int RECORD_TYPE_OPT = 41;           // RFC 2671
    public static final int RECORD_TYPE_APL = 42;           // RFC 3123
    public static final int RECORD_TYPE_DS = 43;            // RFC 4034
    public static final int RECORD_TYPE_SSHFP = 44;         // RFC 4255
    public static final int RECORD_TYPE_IPSECKEY = 45;      // RFC 4025
    public static final int RECORD_TYPE_RRSIG = 46;         // RFC 4034
    public static final int RECORD_TYPE_NSEC = 47;          // RFC 4034
    public static final int RECORD_TYPE_DNSKEY = 48;        // RFC 4034
    public static final int RECORD_TYPE_DHCID = 49;         // RFC 4701
    public static final int RECORD_TYPE_NSEC3 = 50;         // RFC ????
    public static final int RECORD_TYPE_NSEC3PARAM = 51;    // RFC ????
    public static final int RECORD_TYPE_HIP = 55;           // RFC 5205
    public static final int RECORD_TYPE_SPF = 99;           // RFC 4408
    public static final int RECORD_TYPE_UINFO = 100;        // RFC ????
    public static final int RECORD_TYPE_UID = 101;          // RFC ????
    public static final int RECORD_TYPE_GID = 102;          // RFC ????
    public static final int RECORD_TYPE_UNSPEC = 103;       // RFC ????
    public static final int RECORD_TYPE_TKEY = 249;         // RFC 2930
    public static final int RECORD_TYPE_TSIG = 250;         // RFC 2931
    public static final int RECORD_TYPE_IXFR = 251;         // RFC 1995
    public static final int RECORD_TYPE_AXFR = 252;         // RFC 1035
    public static final int RECORD_TYPE_MAILB = 253;        // RFC 1035
    public static final int RECORD_TYPE_MAILA = 254;        // RFC 1035
    public static final int RECORD_TYPE_ANY = 255;          // RFC 1035
    public static final int RECORD_TYPE_DLV = 32769;        // RFC 4431

    public static class Flags {
        boolean mQR = false;
        int mOP = 0;    // 4 bits.
        boolean mAA = false;
        boolean mTC = false;
        boolean mRD = false;
        boolean mRA = false;
        boolean mUN = false;
        boolean mAD = false;
        boolean mCD = false;
        int mRC = 0;    // 4 bits.

        Flags() {}

        public static Flags decodeInt(int value) {
            Flags flags = new Flags();
            flags.setQR(((value & 0x8000) >> 15) != 0);
            flags.setOP((value & 0x7800) >> 11);
            flags.setAA(((value & 0x0400) >> 10) != 0);
            flags.setTC(((value & 0x0200) >> 9) != 0);
            flags.setRD(((value & 0x0100) >> 8) != 0);
            flags.setRA(((value & 0x0080) >> 7) != 0);
            flags.setUN(((value & 0x0040) >> 6) != 0);
            flags.setAD(((value & 0x0020) >> 5) != 0);
            flags.setCD(((value & 0x0010) >> 4) != 0);
            flags.setRC((value & 0x000f) >> 0);
            return flags;
        }

        public int encodeInt() {
            int result = 0;
            result |= (mQR ? 0x8000 : 0x0000);
            result |= (mOP << 11) & 0x7800;
            result |= (mAA ? 0x0400 : 0x0000);
            result |= (mTC ? 0x0200 : 0x0000);
            result |= (mRD ? 0x0100 : 0x0000);
            result |= (mRA ? 0x0080 : 0x0000);
            result |= (mUN ? 0x0040 : 0x0000);
            result |= (mAD ? 0x0020 : 0x0000);
            result |= (mCD ? 0x0010 : 0x0000);
            result |= mRC & 0x000f;
            return result;
        }

        boolean getQR() {
            return mQR;
        }
        void setQR(boolean QR) {
            mQR = QR;
        }

        int getOP() {
            return mOP;
        }
        void setOP(int OP) {
            mOP = OP;
        }

        boolean getAA() {
            return mAA;
        }
        void setAA(boolean AA) {
            mAA = AA;
        }

        boolean getTC() {
            return mTC;
        }
        void setTC(boolean TC) {
            mTC = TC;
        }

        boolean getRD() {
            return mRD;
        }
        void setRD(boolean RD) {
            mRD = RD;
        }

        boolean getRA() {
            return mRA;
        }
        void setRA(boolean RA) {
            mRA = RA;
        }

        boolean getUN() {
            return mUN;
        }
        void setUN(boolean UN) {
            mUN = UN;
        }

        boolean getAD() {
            return mAD;
        }
        void setAD(boolean AD) {
            mAD = AD;
        }

        boolean getCD() {
            return mCD;
        }
        void setCD(boolean CD) {
            mCD = CD;
        }

        int getRC() {
            return mRC;
        }
        void setRC(int RC) {
            mRC = RC;
        }

        @Override
        public String toString() {
            return "Flags{" +
                    "QR=" + mQR + "," +
                    "OP=" + mOP + "," +
                    "AA=" + mAA + "," +
                    "mTC=" + mTC + "," +
                    "RD=" + mRD + "," +
                    "RA=" + mRA + "," +
                    "UN=" + mUN + "," +
                    "AD=" + mAD + "," +
                    "CD=" + mCD + "," +
                    "RC=" + mRC + "}";
        }
    }

    public static enum SectionType {
        QUESTION,
        ANSWER,
        AUTHORITY,
        ADDITIONAL
    }

    Flags mFlags = null;
    List<QuestionRecord> mQuestionRecords = new ArrayList<>();
    List<ResourceRecord> mAnswerRecords = new ArrayList<>();
    List<ResourceRecord> mAuthorityRecords = new ArrayList<>();
    List<ResourceRecord> mAdditionalRecords = new ArrayList<>();

    DNSPacket() {
    }

    public Flags getFlags() {
        return mFlags;
    }
    public void setFlags(Flags flags) {
        mFlags = flags;
    }

    public void addQuestionRecord(QuestionRecord question) {
        mQuestionRecords.add(question);
    }

    public List<QuestionRecord> getQuestionRecords() {
        return mQuestionRecords;
    }
    public List<ResourceRecord> getAnswerRecords() {
        return mAnswerRecords;
    }
    public List<ResourceRecord> getAuthorityRecords() {
        return mAuthorityRecords;
    }
    public List<ResourceRecord> getAdditionalRecords() {
        return mAdditionalRecords;
    }

    public void parsePacket(byte[] packetData) throws IOException {
        PacketParser parser = new PacketParser(packetData);

        // ID must be zero (2 bytes)
        int id = parser.readInt16();
        if (id != 0) {
            throw new IOException("Invalid DNSPacket id != 0");
        }

        // Read flags (2 bytes)
        int flags = parser.readInt16();
        mFlags = Flags.decodeInt(flags);

        Log.e("DNSPacket", "Parsed flags: " + mFlags.toString());

        // Read number of records for each type.
        int numQuestions = parser.readInt16();
        int numAnswers = parser.readInt16();
        int numAuthority = parser.readInt16();
        int numAdditional = parser.readInt16();

        Log.e("DNSPacket",
                "qs=" + numQuestions + ", an=" + numAnswers +
                ", au=" + numAuthority + ", ad=" + numAdditional);

        // Parse question records.
        for (int i = 0; i < numQuestions; i++) {
            QuestionRecord qr = new QuestionRecord();
            Log.e("DNSPacket", "Parsing question " + i);
            qr.parse(parser);
            Log.e("DNSPacket", "Parsed question " + i + ": " + qr.toString());
            mQuestionRecords.add(qr);
        }

        // Parse answer records.
        for (int i = 0; i < numAnswers; i++) {
            ResourceRecord rr = new ResourceRecord();
            Log.e("DNSPacket", "Parsing answer " + i);
            rr.parse(parser);
            Log.e("DNSPacket", "Parsed answer " + i + ": " + rr.toString());
            mAnswerRecords.add(rr);
        }

        // Parse authority records.
        for (int i = 0; i < numAuthority; i++) {
            ResourceRecord rr = new ResourceRecord();
            Log.e("DNSPacket", "Parsing authority " + i);
            rr.parse(parser);
            Log.e("DNSPacket", "Parsed authority " + i + ": " + rr.toString());
            mAuthorityRecords.add(rr);
        }

        // Parse additional records.
        for (int i = 0; i < numAdditional; i++) {
            ResourceRecord rr = new ResourceRecord();
            Log.e("DNSPacket", "Parsing additional " + i);
            rr.parse(parser);
            Log.e("DNSPacket", "Parsed additional " + i + ": " + rr.toString());
            mAdditionalRecords.add(rr);
        }
    }

    public byte[] encodePacket() throws IOException {
        PacketEncoder encoder = new PacketEncoder();

        if (!mAnswerRecords.isEmpty() ||
            !mAuthorityRecords.isEmpty() ||
            !mAdditionalRecords.isEmpty())
        {
            throw new IOException("Cannot encode non-question records: UNIMPLEMENTED");
        }

        // Write 16-bit 0 id.
        encoder.writeInt16(0x0000);
        encoder.writeInt16(mFlags.encodeInt());

        encoder.writeInt16(mQuestionRecords.size());
        encoder.writeInt16(0x0000);
        encoder.writeInt16(0x0000);
        encoder.writeInt16(0x0000);

        // Parse question records.
        for (QuestionRecord qr : mQuestionRecords) {
            qr.encode(encoder);
        }

        return encoder.getBytes();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DNSPacket<<< ");
        builder.append("flags=" + mFlags.encodeInt() + "\n");
        for (QuestionRecord qr : mQuestionRecords) {
            builder.append(qr.toString() + "\n");
        }
        for (ResourceRecord rr : mAnswerRecords) {
            builder.append(rr.toString() + "\n");
        }
        for (ResourceRecord rr : mAuthorityRecords) {
            builder.append(rr.toString() + "\n");
        }
        for (ResourceRecord rr : mAdditionalRecords) {
            builder.append(rr.toString() + "\n");
        }
        builder.append(" >>>");
        return builder.toString();
    }
}
