package ca.vijayan.flyweb.mdns;

import android.util.Log;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
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
            long expiryIntervalSeconds = mRecord.getTTL();
            long timeNow = new Date().getTime();
            return (timeNow - mTimeAdded.getTime()) >= expiryIntervalSeconds;
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
    }

    public static interface Listener {
        void onDNSServiceFound(DNSServiceInfo svc);
        void onDNSServiceChanged(DNSServiceInfo svc, DNSServiceInfo oldsvc);
        void onDNSServiceLost(DNSServiceInfo svc);
    }

    // Map of PTR records for names.
    Map<Key, Set<CachedRecord>> mPTRRecords;
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
            // Store the A record.
            mARecords.put(key, cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_PTR) {
            Set<CachedRecord> crset = mPTRRecords.get(rr.getName());
            if (crset == null) {
                crset = new HashSet<>();
                mPTRRecords.put(key, crset);
            }
            crset.add(cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_SRV) {
            mSRVRecords.put(key, cr);
        }

        if (rr.getRecordType() == DNSPacket.RECORD_TYPE_TXT) {
            mTXTRecords.put(key, cr);
        }
    }

    private void setRemoveExpiredRecordsTimeout() {
        setTimeout(100, new Runnable() {
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
        Log.e("MDNSCache", "removeExpiredRecords()");
        // Remove expired PTR records.
        for (Map.Entry<Key, Set<CachedRecord>> entry : mPTRRecords.entrySet()) {
            List<CachedRecord> toRemove = new ArrayList<>();
            Set<CachedRecord> recs = entry.getValue();
            for (CachedRecord rec : recs) {
                if (rec.isExpired()) {
                    toRemove.add(rec);
                }
            }
            recs.removeAll(toRemove);
        }

        // Remove expired SRV, TXT, and A records.
        removeExpiredRecordsFrom(mSRVRecords);
        removeExpiredRecordsFrom(mTXTRecords);
        removeExpiredRecordsFrom(mARecords);

        updateServiceInfo();
    }

    private static void removeExpiredRecordsFrom(Map<Key, CachedRecord> recordMap) {
        List<Key> toRemove = new ArrayList<>();
        for (Map.Entry<Key, CachedRecord> entry : recordMap.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }
        for (Key key : toRemove) {
            recordMap.remove(key);
        }
    }

    private void updateServiceInfo() {
        List<DNSServiceInfo> foundServices = new ArrayList<>();
        List<DNSServiceInfo> lostServices = new ArrayList<>();
        List<Pair<DNSServiceInfo, DNSServiceInfo>> changedServices = new ArrayList<>();
        recalculateKnownServices(foundServices, lostServices, changedServices);

        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                for (DNSServiceInfo lostService : lostServices) {
                    listener.onDNSServiceFound(lostService);
                }
                for (Pair<DNSServiceInfo, DNSServiceInfo> changedService : changedServices) {
                    listener.onDNSServiceChanged(changedService.first,
                                                 changedService.second);
                }
                for (DNSServiceInfo foundService : foundServices) {
                    listener.onDNSServiceLost(foundService);
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
                found.add(newSvc);
            } else {
                sharedKeys.add(newSvc.getKey());
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
        for (Map.Entry<Key, Set<CachedRecord>> entry : mPTRRecords.entrySet()) {
            for (CachedRecord cr : entry.getValue()) {
                if (cr.isExpired()) {
                    continue;
                }
                DNSServiceInfo svcInfo = resolveService(cr.getRecord());
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

        // Get SRV record.
        CachedRecord srvCached = mSRVRecords.get(svcKey);
        if (srvCached == null) {
            Log.d("MDNSCache", "Did not find SRV entry for PTR.");
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
            Log.d("MDNSCache", "Did not find TXT entry for SRV.");
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
        CachedRecord aCached = mTXTRecords.get(new Key(srvData.getTarget(), false));
        if (aCached == null) {
            Log.d("MDNSCache", "Did not find A entry for SRV.");
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
            Log.d("MDNSCache", "Got UnknownHostException for ip.");
            return null;
        }

        return new DNSServiceInfo(svcType, svcName, txtMap, ipAddr, svcPort);
    }
}
