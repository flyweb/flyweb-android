package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by kvijayan on 08/11/16.
 */

public class MDNSManager {

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

    Thread mQueryThread;
    Thread mReceiveThread;
    Thread mPassiveThread;
    MulticastSocket mPassiveSocket;
    DatagramSocket mQuerySocket;
    Queue<QuestionRecord> mQueryQueue;
    MDNSCache mCache;

    boolean mDone;

    public MDNSManager() {
        mQueryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runQuery();
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
            mQuerySocket = new DatagramSocket();
        } catch (Exception exc) {
            Log.e("MDNSManager", "Failed to open sockets", exc);
            mPassiveSocket = null;
            mQuerySocket = null;
        }
        mQueryQueue = new ConcurrentLinkedQueue<>();
        mCache = new MDNSCache();
        mDone = false;
    }

    public void start() {
        mQueryThread.start();
        mReceiveThread.start();
        mPassiveThread.start();
    }

    synchronized public void stop() {
        mDone = true;
        mQuerySocket.close();
        mPassiveSocket.close();
        this.notify();
        while (mQueryThread.isAlive() || mReceiveThread.isAlive()) {
            try {
                if (mQueryThread.isAlive()) {
                    mQueryThread.join();
                }
                if (mReceiveThread.isAlive()) {
                    mReceiveThread.join();
                }
                if (mPassiveThread.isAlive()) {
                    mPassiveThread.join();
                }
            } catch (InterruptedException exc) {
            }
        }
    }

    public void runQuery() {
        while (!mDone) {
            Log.d("MDNSManager", "runQuery - TOP");

            if (mQuerySocket.isClosed()) {
                break;
            }
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
            mQuerySocket.send(packet);
        } catch (IOException exc) {
            Log.e("MDNSManager", "poll: Failed to send packet", exc);
            return;
        }
    }

    public void runReceive() {
        for (;;) {
            Log.d("MDNSManager", "runReceive - TOP");
            if (mQuerySocket.isClosed()) {
                break;
            }
            receive();
        }
    }

    void receive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mQuerySocket.receive(packet);
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

        // Add the DNS packet to the MDNS cache.
        mCache.addDNSPacket(dnsPacket);

        Log.e("MDNSManager", "GOT PACKET: " + dnsPacket.toString());
    }
}
