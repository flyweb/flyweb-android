package ca.vijayan.flyweb.mdns;

import android.util.Log;
import android.util.Pair;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by kannanvijayan on 2016-11-09.
 */

public class MDNSCache extends WorkerThread {
    static class CachedRecord {
        Date mTimeAdded;
        ResourceRecord mRecord;

        CachedRecord(ResourceRecord record) {
            mTimeAdded = new Date();
            mRecord = record;
            if (mRecord.getTTL() == 0) {
                // Set TTL to 1 for expiring 0-ttl records.
                mRecord.setTTL(1);
            }
        }

        Date getTimeAdded() {
            return mTimeAdded;
        }
        ResourceRecord getRecord() {
            return mRecord;
        }

        boolean isExpired() {
            long expiryIntervalMs = mRecord.getTTL() * 1000;
            long timeNow = new Date().getTime();
            return (timeNow - mTimeAdded.getTime()) >= expiryIntervalMs;
        }
    }

    static class Key {
        List<String> mName;

        Key(List<String> name) {
            mName = new ArrayList<>(name);
        }
        Key(List<String> name, boolean copy) {
            if (copy) {
                mName = new ArrayList<>(name);
            } else {
                mName = name;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof Key)) {
                return false;
            }

            Key otherKey = (Key) other;
            return mName.equals(otherKey.mName);
        }

        @Override
        public int hashCode() {
            return mName.hashCode();
        }

        @Override
        public String toString() {
            return mName.toString();
        }
    }

    public static interface Listener {
        void onDNSServiceFound(DNSServiceInfo svc);
        void onDNSServiceChanged(DNSServiceInfo svc, DNSServiceInfo oldsvc);
        void onDNSServiceLost(DNSServiceInfo svc);
    }

    // Map of PTR records for names.
    Map<Key, Map<Key, CachedRecord>> mPTRRecords;
    Map<Key, CachedRecord> mSRVRecords;
    Map<Key, CachedRecord> mTXTRecords;
    Map<Key, CachedRecord> mARecords;

    Map<DNSServiceInfo.Key, DNSServiceInfo> mKnownServices;
    Set<Listener> mListeners;

    public MDNSCache() {
        super("MDNSCache");
        mPTRRecords = new HashMap<>();
        mSRVRecords = new HashMap<>();
        mTXTRecords = new HashMap<>();
        mARecords = new HashMap<>();
        mKnownServices = new HashMap<>();
        mListeners = new HashSet<>();
    }

    public void addListener(final Listener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }

        // Immediately call onFound for all known services.
        addRunnable(new Runnable() {
            @Override
            public void run() {
                for (DNSServiceInfo.Key key : mKnownServices.keySet()) {
                    listener.onDNSServiceFound(mKnownServices.get(key));
                }
            }
        });
    }

    public void removeListener(final Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    @Override
    protected void onStart() {
        setRemoveExpiredRecordsTimeout();
    }

    public void addDNSPacket(final DNSPacket dnsPacket) {
        // Ignore query packets.
        DNSPacket.Flags flags = dnsPacket.getFlags();
        if (flags.getQR() == DNSPacket.QR_CODE_QUERY) {
            return;
        }

        // Ignore non-authoritative answers.
        if (!flags.getAA()) {
            return;
        }

        // Schedule the rest of the packet-adding on the
        // cache thread.
        this.addRunnable(new Runnable() {
            @Override
            public void run() {
                // Process answer and additional records.
                for (ResourceRecord rr : dnsPacket.getAnswerRecords()) {
                    addResourceRecord(rr);
                }
                for (ResourceRecord rr : dnsPacket.getAdditionalRecords()) {
                    addResourceRecord(rr);
                }
                updateServiceInfo();
            }
        });
    }

    private void addResourceRecord(ResourceRecord rr) {
        if (rr.getClassCode() != DNSPacket.CLASS_CODE_IN) {
            return;
        }

        CachedRecord cr = new CachedRecord(rr);
        Key key = new Key(rr.getName());

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_A) {
            // Log.d("MDNSCache", "Adding A record: " + rr.toString());
            // Store the A record.
            mARecords.put(key, cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_PTR) {
            // Log.d("MDNSCache", "Adding PTR record: " + rr.toString());
            Map<Key, CachedRecord> crset = mPTRRecords.get(key);
            if (crset == null) {
                crset = new HashMap<>();
                mPTRRecords.put(key, crset);
            }
            crset.put(new Key(cr.getRecord().getPTRData().getServiceName()), cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_SRV) {
            // Log.d("MDNSCache", "Adding SRV record: " + rr.toString());
            mSRVRecords.put(key, cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_TXT) {
            // Log.d("MDNSCache", "Adding TXT record: " + rr.toString());
            mTXTRecords.put(key, cr);
        }
    }

    private static final int CHECK_EXPIRED_RECORDS_INTERVAL = 100;
    private void setRemoveExpiredRecordsTimeout() {
        setTimeout(CHECK_EXPIRED_RECORDS_INTERVAL, new Runnable() {
            @Override
            public void run() {
                doRemoveExpiredRecordsAndReschedule();
            }
        });
    }
    private void doRemoveExpiredRecordsAndReschedule() {
        removeExpiredRecords();
        setRemoveExpiredRecordsTimeout();
    }

    public void removeExpiredRecords() {
        // Remove expired PTR records.
        int numRemoved = 0;
        for (Map.Entry<Key, Map<Key, CachedRecord>> entry : mPTRRecords.entrySet()) {
            List<Key> toRemove = new ArrayList<>();
            Map<Key, CachedRecord> recs = entry.getValue();
            for (Map.Entry<Key, CachedRecord> rec : recs.entrySet()) {
                if (rec.getValue().isExpired()) {
                    toRemove.add(rec.getKey());
                }
            }
            for (Key rk : toRemove) {
                Log.d("MDNSCache", "Removing expired PTR record: " + rk.toString());
                recs.remove(rk);
            }
            numRemoved += toRemove.size();
        }

        // Remove expired SRV, TXT, and A records.
        numRemoved += removeExpiredRecordsFrom(mSRVRecords, "SRV");
        numRemoved += removeExpiredRecordsFrom(mTXTRecords, "TXT");
        numRemoved += removeExpiredRecordsFrom(mARecords, "A");

        if (numRemoved > 0) {
            updateServiceInfo();
        }
    }

    private static int removeExpiredRecordsFrom(Map<Key, CachedRecord> recordMap,
                                                String type)
    {
        List<Key> toRemove = new ArrayList<>();
        for (Map.Entry<Key, CachedRecord> entry : recordMap.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }
        for (Key key : toRemove) {
            Log.d("MDNSCache", "Removing expired " + type + " record: " + key.mName.toString());
            recordMap.remove(key);
        }
        return toRemove.size();
    }

    private void updateServiceInfo() {
        Log.d("MDNSCache", "updateServiceInfo()");
        for (Map.Entry<Key, Map<Key, CachedRecord>> entry : mPTRRecords.entrySet()) {
            Log.d("MDNSCache", "PTR " + entry.getKey().toString() + " {");
            for (Map.Entry<Key, CachedRecord> cr : entry.getValue().entrySet()) {
                Log.d("MDNSCache", "    " + cr.getValue().getRecord().getPTRData().toString());
            }
            Log.d("MDNSCache", "}");
        }

        List<DNSServiceInfo> foundServices = new ArrayList<>();
        List<DNSServiceInfo> lostServices = new ArrayList<>();
        List<Pair<DNSServiceInfo, DNSServiceInfo>> changedServices = new ArrayList<>();
        recalculateKnownServices(foundServices, lostServices, changedServices);

        for (DNSServiceInfo found : foundServices) {
            Log.d("MDNSCache", "Found service: " + found.getKey().toString());
        }
        for (DNSServiceInfo lost : lostServices) {
            Log.d("MDNSCache", "Lost service: " + lost.getKey().toString());
        }
        for (Pair<DNSServiceInfo, DNSServiceInfo> changed : changedServices) {
            Log.d("MDNSCache", "Changed service: " + changed.first.getKey().toString());
        }

        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                Log.d("MDNSCache", "Calling listener: " + listener);
                for (DNSServiceInfo lostService : lostServices) {
                    listener.onDNSServiceLost(lostService);
                }
                for (Pair<DNSServiceInfo, DNSServiceInfo> changedService : changedServices) {
                    listener.onDNSServiceChanged(changedService.first,
                            changedService.second);
                }
                for (DNSServiceInfo foundService : foundServices) {
                    listener.onDNSServiceFound(foundService);
                }
            }
        }
    }

    private void recalculateKnownServices(
            Collection<DNSServiceInfo> found,
            Collection<DNSServiceInfo> lost,
            Collection<Pair<DNSServiceInfo, DNSServiceInfo>> changed)
    {
        Set<DNSServiceInfo> newKnownServices = computeKnownServiceSet();
        Set<DNSServiceInfo.Key> sharedKeys = new HashSet<>();

        // Any service in newKnownServices that's not in mKnownServices, is found.
        for (DNSServiceInfo newSvc : newKnownServices) {
            if (mKnownServices.containsKey(newSvc.getKey())) {
                sharedKeys.add(newSvc.getKey());
            } else {
                found.add(newSvc);
            }
        }

        // Any service in mKnownServices that's not in newKnownServices, is lost.
        for (DNSServiceInfo.Key oldSvcKey : mKnownServices.keySet()) {
            if (!sharedKeys.contains(oldSvcKey)) {
                lost.add(oldSvcKey.getServiceInfo());
            }
        }

        for (DNSServiceInfo foundSvcInfo : found) {
            mKnownServices.put(foundSvcInfo.getKey(), foundSvcInfo);
        }
        for (DNSServiceInfo lostSvcInfo : lost) {
            mKnownServices.remove(lostSvcInfo.getKey());
        }

        // Update the service info for shared entries.
        for (DNSServiceInfo.Key sharedKey : sharedKeys) {
            DNSServiceInfo svc = mKnownServices.get(sharedKey);
            if (svc.isUpdated(sharedKey.getServiceInfo())) {
                DNSServiceInfo oldClone = svc.clone();
                svc.updateWith(sharedKey.getServiceInfo());
                changed.add(new Pair<>(svc, oldClone));
            }
        }
    }

    private Set<DNSServiceInfo> computeKnownServiceSet() {
        Set<DNSServiceInfo> result = new HashSet<>();
        for (Map.Entry<Key, Map<Key, CachedRecord>> entry : mPTRRecords.entrySet()) {
            for (Map.Entry<Key, CachedRecord> cr : entry.getValue().entrySet()) {
                if (cr.getValue().isExpired()) {
                    continue;
                }
                DNSServiceInfo svcInfo = resolveService(cr.getValue().getRecord());
                if (svcInfo != null) {
                    result.add(svcInfo);
                }
            }
        }
        return result;
    }

    private DNSServiceInfo resolveService(ResourceRecord rr) {
        List<String> svcType = rr.getName();

        assert (rr.getRecordType() == DNSPacket.RECORD_TYPE_PTR);
        ResourceData resourceData = rr.getResourceData();
        assert (resourceData != null);
        assert (resourceData instanceof ResourceData.PTR);
        ResourceData.PTR ptrData = (ResourceData.PTR) resourceData;

        List<String> svcName = ptrData.getServiceName();
        Key svcKey = new Key(svcName, false);

        // Log.d("MDNSCache", "resolveService() resolving: [" + svcName.toString() + "]");

        // Get SRV record.
        CachedRecord srvCached = mSRVRecords.get(svcKey);
        if (srvCached == null) {
            Log.e("MDNSCache", "Did not find SRV entry for PTR " + svcKey.toString());
            return null;
        }
        ResourceRecord srvRecord = srvCached.getRecord();
        assert (srvRecord.getRecordType() == DNSPacket.RECORD_TYPE_SRV);
        resourceData = srvRecord.getResourceData();
        assert (resourceData != null);
        assert (resourceData instanceof ResourceData.SRV);
        ResourceData.SRV srvData = (ResourceData.SRV) resourceData;
        int svcPort = srvData.getPort();

        // get TXT record.
        CachedRecord txtCached = mTXTRecords.get(svcKey);
        if (txtCached == null) {
            Log.e("MDNSCache", "Did not find TXT entry for SRV " + svcKey.toString());
            return null;
        }
        ResourceRecord txtRecord = txtCached.getRecord();
        assert (txtRecord.getRecordType() == DNSPacket.RECORD_TYPE_TXT);
        resourceData = txtRecord.getResourceData();
        assert (resourceData != null);
        assert (resourceData instanceof ResourceData.TXT);
        ResourceData.TXT txtData = (ResourceData.TXT) resourceData;
        Map<String, byte[]> txtMap = txtData.getMap();

        // get A record.
        CachedRecord aCached = mARecords.get(new Key(srvData.getTarget(), false));
        if (aCached == null) {
            Log.e("MDNSCache", "Did not find A entry for SRV " + srvData.getTarget().toString());
            return null;
        }
        ResourceRecord aRecord = aCached.getRecord();
        assert (aRecord.getRecordType() == DNSPacket.RECORD_TYPE_A);
        resourceData = aRecord.getResourceData();
        assert (resourceData != null);
        assert (resourceData instanceof ResourceData.A);
        ResourceData.A aData = (ResourceData.A) resourceData;
        InetAddress ipAddr;
        try {
            ipAddr = Inet4Address.getByAddress(aData.getIp());
        } catch (UnknownHostException exc) {
            Log.e("MDNSCache", "Got UnknownHostException for ip.");
            return null;
        }

        // Log.d("MDNSCache", "resolveService() resolved: [" + svcName.toString() + "]");

        return new DNSServiceInfo(svcType, svcName, txtMap, ipAddr, svcPort);
    }
}
