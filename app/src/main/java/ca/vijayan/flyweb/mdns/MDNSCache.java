package ca.vijayan.flyweb.mdns;

import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by kannanvijayan on 2016-11-09.
 */

public class MDNSCache {
    static class CachedRecord {
        Date mTimeAdded;
        QuestionRecord mQuestion;
        ResourceRecord mRecord;

        CachedRecord(QuestionRecord question, ResourceRecord record) {
            mTimeAdded = new Date();
            mQuestion = question;
            mRecord = record;
            if (mRecord.getTTL() == 0) {
                // Set TTL to 1 for expiring 0-ttl records.
                mRecord.setTTL(1);
            }
        }

        Date getTimeAdded() {
            return mTimeAdded;
        }
        QuestionRecord getQuestion() {
            return mQuestion;
        }
        ResourceRecord getRecord() {
            return mRecord;
        }

        boolean isExpired() {
            long expiryIntervalSeconds = mRecord.getTTL();
            long timeNow = new Date().getTime();
            return (timeNow - mTimeAdded.getTime()) >= expiryIntervalSeconds;
        }
    }

    static class ListenerKey {
        int mRecordType;
        List<String> mName;
        int mHashCode;

        ListenerKey(int recordType, List<String> name) {
            mRecordType = recordType;
            mName = name;
            mHashCode = 0;
            calculateHashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof ListenerKey)) {
                return false;
            }

            ListenerKey otherListenerKey = (ListenerKey) other;
            return (mRecordType == otherListenerKey.mRecordType) &&
                    mName.equals(otherListenerKey.mName);
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
            } catch (Exception exc) {
                throw new RuntimeException("Unexpected error calculating hash code", exc);
            }
            mHashCode = Arrays.hashCode(outs.toByteArray());
        }
    }

    public static interface Listener {
        void onFound(ResourceRecord rr);
        void onChanged(ResourceRecord rr, ResourceRecord oldrr);
        void onRemoved(ResourceRecord rr);
    }

    // Map of all cached records by type.
    // Keys of map are taken from DNSPacket.RECORD_TYPE_* values.
    Map<Integer, Map<QuestionRecord, CachedRecord>> mCachedRecords;
    Map<ListenerKey, Set<Listener>> mListeners;

    public MDNSCache() {
        mCachedRecords = new HashMap<>();
        mListeners = new HashMap<>();
    }

    public synchronized Set<Listener> lookupListeners(int recordType, List<String> name) {
        Set<Listener> listeners = mListeners.get(new ListenerKey(recordType, name));
        if (listeners == null) {
            return new HashSet<Listener>();
        }
        return new HashSet<Listener>(listeners);
    }

    public void addRecord(QuestionRecord question, ResourceRecord record) {
        Set<Listener> listeners = null;
        ResourceRecord currentRecord = null;

        synchronized (this) {
            int recordType = record.getRecordType();
            Map<QuestionRecord, CachedRecord> recordMap = mCachedRecords.get(recordType);
            if (recordMap == null) {
                recordMap = new HashMap<QuestionRecord, CachedRecord>();
                mCachedRecords.put(recordType, recordMap);
            }

            listeners = lookupListeners(recordType, question.getName());
            CachedRecord currentCached = recordMap.get(question);
            if (currentCached != null) {
                currentRecord = currentCached.getRecord();
            }
            recordMap.put(question, new CachedRecord(question, record));
        }

        for (Listener listener : listeners) {
            if (currentRecord == null) {
                listener.onFound(record);
            } else {
                listener.onChanged(record, currentRecord);
            }
        }
    }

    public synchronized ResourceRecord lookupRecord(QuestionRecord question) {
        int recordType = question.getRecordType();
        Map<QuestionRecord, CachedRecord> recordMap = mCachedRecords.get(recordType);
        if (recordMap == null) {
            return null;
        }

        CachedRecord cachedRecord = recordMap.get(question);
        if (cachedRecord == null) {
            return null;
        }

        return cachedRecord.getRecord();
    }

    public void removeExpiredRecords() {
        List<Pair<Listener, ResourceRecord>> listeners = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Integer, Map<QuestionRecord, CachedRecord>> entry : mCachedRecords.entrySet()) {
                Set<QuestionRecord> toRemove = new HashSet<>();
                Map<QuestionRecord, CachedRecord> recordMap = entry.getValue();
                for (Map.Entry<QuestionRecord, CachedRecord> recordEntry : recordMap.entrySet()) {
                    CachedRecord cr = recordEntry.getValue();
                    ResourceRecord rr = cr.getRecord();
                    if (cr.isExpired()) {
                        toRemove.add(recordEntry.getKey());
                        for (Listener listener : lookupListeners(rr.getRecordType(), rr.getName())) {
                            listeners.add(new Pair<Listener, ResourceRecord>(listener, rr));
                        }
                    }
                }
                for (QuestionRecord qr : toRemove) {
                    recordMap.remove(qr);
                }
            }
        }

        for (Pair<Listener, ResourceRecord> lrr : listeners) {
            lrr.first.onRemoved(lrr.second);
        }
    }

    public synchronized void addDNSPacket(DNSPacket dnsPacket) {
        // Ignore query packets.
        DNSPacket.Flags flags = dnsPacket.getFlags();
        if (flags.getQR() == DNSPacket.QR_CODE_QUERY) {
            return;
        }

        // Ignore non-authoritative answers.
        if (!flags.getAA()) {
            return;
        }

        // Process answer and additional records.
        for (ResourceRecord rr : dnsPacket.getAnswerRecords()) {
            addResourceRecord(rr);
        }
        for (ResourceRecord rr : dnsPacket.getAdditionalRecords()) {
            addResourceRecord(rr);
        }
    }

    private void addResourceRecord(ResourceRecord rr) {
        if (rr.getClassCode() != DNSPacket.CLASS_CODE_IN) {
            return;
        }
        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_A ||
            rr.getRecordType() == DNSPacket.RECORD_TYPE_PTR ||
            rr.getRecordType() == DNSPacket.RECORD_TYPE_SRV ||
            rr.getRecordType() == DNSPacket.RECORD_TYPE_TXT)
        {
            addRecord(makeQuestionRecordFor(rr), rr);
        }
    }

    private static QuestionRecord makeQuestionRecordFor(ResourceRecord rr) {
        return new QuestionRecord(rr.getName(), rr.getRecordType(), rr.getClassCode(), true);
    }
}
