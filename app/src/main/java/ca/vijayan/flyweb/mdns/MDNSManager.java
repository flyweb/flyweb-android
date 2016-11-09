package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by kvijayan on 08/11/16.
 */

public class MDNSManager {
    static class CachedRecord {
        Date mTimeAdded;
        QuestionRecord mQuestion;
        ResourceRecord mRecord;

        CachedRecord(QuestionRecord question, ResourceRecord record) {
            mTimeAdded = new Date();
            mQuestion = question;
            mRecord = record;
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

    public synchronized void addRecord(QuestionRecord question, ResourceRecord record) {
        int recordType = record.getRecordType();
        Map<QuestionRecord, CachedRecord> recordMap = mCachedRecords.get(recordType);
        if (recordMap == null) {
            recordMap = new HashMap<QuestionRecord, CachedRecord>();
            mCachedRecords.put(recordType, recordMap);
        }

        recordMap.put(question, new CachedRecord(question, record));
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

    public synchronized void removeExpiredRecords() {
        for (Map.Entry<Integer, Map<QuestionRecord, CachedRecord>> entry : mCachedRecords.entrySet()) {
            Set<QuestionRecord> toRemove = new HashSet<>();
            for (Map.Entry<QuestionRecord, CachedRecord> recordEntry : entry.getValue().entrySet()) {
                if (recordEntry.getValue().isExpired()) {
                    toRemove.add(recordEntry.getKey());
                }
            }
            for (QuestionRecord qr : toRemove) {
                entry.getValue().remove(qr);
            }
        }
    }

    static byte[] MDNS_ADDRESS_BYTES = new byte[] { (byte)224, 0, 0, (byte)251 };
    static InetAddress getMDNSAddress() {
        try {
            return Inet4Address.getByAddress(MDNS_ADDRESS_BYTES);
        } catch (UnknownHostException exc) {
            Log.e("MDNSManager", "getMDNSAddress failed (SHOULD NOT HAPPEN)");
            return null;
        }
    }
    static int MDNS_PORT = 5353;

    Thread mPollThread;
    Thread mReceiveThread;
    Thread mPassiveThread;
    MulticastSocket mPassiveSocket;
    DatagramSocket mSocket;

    // Map of all cached records by type.
    // Keys of map are taken from DNSPacket.RECORD_TYPE_* values.
    Map<Integer, Map<QuestionRecord, CachedRecord>> mCachedRecords;
    Queue<QuestionRecord> mPollQueue;

    boolean mDone;

    public MDNSManager() {
        mPollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runPoll();
            }
        });
        mReceiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runReceive();
            }
        });
        mPassiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runPassive();
            }
        });

        try {
            mPassiveSocket = new MulticastSocket(MDNS_PORT);
            mPassiveSocket.joinGroup(getMDNSAddress());
            mSocket = new DatagramSocket();
        } catch (Exception exc) {
            Log.e("MDNSManager", "Failed to open sockets", exc);
            mPassiveSocket = null;
            mSocket = null;
        }
        mCachedRecords = new HashMap<>();
        mPollQueue = new ConcurrentLinkedQueue<>();
        mDone = false;
    }

    public void start() {
        mPollThread.start();
        mReceiveThread.start();
        mPassiveThread.start();
    }

    synchronized public void stop() {
        mDone = true;
        this.notify();
        while (mPollThread.isAlive() || mReceiveThread.isAlive()) {
            try {
                if (mPollThread.isAlive()) {
                    mPollThread.join();
                }
                if (mReceiveThread.isAlive()) {
                    mReceiveThread.join();
                }
            } catch (InterruptedException exc) {
            }
        }
    }

    public void runPoll() {
        while (!mDone) {
            Log.d("MDNSManager", "runPoll - TOP");
            //poll();
            try {
                synchronized(this) {
                    this.wait(10000);
                }
            } catch (InterruptedException exc) {
            }
        }
    }

    void poll() {
        // Create a question for '_flyweb._tcp'.
        List<String> name = PacketEncoder.dottedToName("_http._tcp.local");
        QuestionRecord question = new QuestionRecord(

                /* name = */ name,
                /* recordType = */ DNSPacket.RECORD_TYPE_PTR,
                /* classCode = */ DNSPacket.CLASS_CODE_IN,
                /* cacheFlush = */ true);
        DNSPacket.Flags flags = new DNSPacket.Flags();
        flags.setQR(DNSPacket.QR_CODE_QUERY);

        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setFlags(flags);
        dnsPacket.addQuestionRecord(question);

        byte[] packetData = null;
        try {
            packetData = dnsPacket.encodePacket();
        } catch (IOException exc) {
            Log.e("MDNSManager", "poll: Failed to encode question packet", exc);
            return;
        }

        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                                                   getMDNSAddress(), MDNS_PORT);

        try {
            mSocket.send(packet);
        } catch (IOException exc) {
            Log.e("MDNSManager", "poll: Failed to send packet", exc);
            return;
        }
    }

    public void runReceive() {
        for (;;) {
            Log.d("MDNSManager", "runReceive - TOP");
            receive();
        }
    }

    void receive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mSocket.receive(packet);
        } catch (IOException exc) {
            Log.e("MDNSManager", "poll: Failed to receive packet", exc);
            return;
        }

        Log.e("MDNSManager", "RECEIVED PACKET OF LENGTH: " + packet.getLength());
        byte[] packetData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        Log.e("MDNSManager", "PACKET DATA: " + Arrays.toString(packetData));

        /*
        DNSPacket dnsPacket = new DNSPacket();
        try {
            dnsPacket.parsePacket(packetData);
        } catch (IOException exc) {
            Log.e("MDNSManager", "Error parsing DNS packet", exc);
            return;
        }
        */
    }

    public void runPassive() {
        for (;;) {
            Log.d("MDNSManager", "runPassive - TOP");
            passiveReceive();
        }
    }

    void passiveReceive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mPassiveSocket.receive(packet);
        } catch (IOException exc) {
            Log.e("MDNSManager", "poll: Failed to receive passive packet", exc);
            return;
        }

        Log.e("MDNSManager", "RECEIVED PASSIVE PACKET OF LENGTH: " + packet.getLength());
        byte[] packetData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        Log.e("MDNSManager", "PASSIVE PACKET DATA: " + Arrays.toString(packetData));

        DNSPacket dnsPacket = new DNSPacket();
        try {
            dnsPacket.parsePacket(packetData);
        } catch (IOException exc) {
            Log.e("MDNSManager", "Error parsing DNS packet", exc);
            return;
        }

        Log.e("MDNSManager", "GOT PACKET: " + dnsPacket.toString());
    }
}
