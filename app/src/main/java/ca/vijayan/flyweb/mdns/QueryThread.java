package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Kannan Vijayan on 11/16/2016.
 */

public class QueryThread extends Thread {
    DatagramSocket mSocket;
    Queue<QuestionRecord> mQueue;
    MDNSCache mCache;
    boolean mStop;
    Date mLastQueryTime;

    private static final int RECV_TIMEOUT = 1000;

    public QueryThread(DatagramSocket socket, MDNSCache cache)
        throws IOException
    {
        super();
        mSocket = socket;
        mSocket.setSoTimeout(RECV_TIMEOUT);
        mQueue = new ConcurrentLinkedQueue<>();
        mCache = cache;
        mStop = false;
        mLastQueryTime = null;
    }

    public void shutdown() {
        synchronized (this) {
            mStop = true;
        }
        synchronized (mQueue) {
            mQueue.notify();
        }

        while (this.isAlive()) {
            try {
                this.join();
            } catch (InterruptedException exc) {
                continue;
            }
        }
    }

    private static final int POLL_WAIT = 1000;
    private static final int ISSUE_QUERY_INTERVAL = 5000;

    @Override
    public void run() {
        mLastQueryTime = null;
        mQueue.add(makeFlyWebQuestion());

        TOP: for (;;) {
            // Check for stop.
            synchronized (this) {
                if (mStop) {
                    break;
                }
            }

            // Issue any query requests.
            while (!mQueue.isEmpty()) {
                QuestionRecord qr = mQueue.poll();
                query(qr);
            }

            // Listen for any responses.
            receive();
        }
    }

    private static QuestionRecord makeFlyWebQuestion() {
        // Create a question for '_flyweb._tcp'.
        List<String> name = PacketEncoder.dottedToName("_http._tcp.local");
        return new QuestionRecord(
                /* name = */ name,
                /* recordType = */ DNSPacket.RECORD_TYPE_PTR,
                /* classCode = */ DNSPacket.CLASS_CODE_IN,
                /* cacheFlush = */ true);
    }

    private void query(QuestionRecord question) {
        Log.e("QueryThread", "query: " + question.toString());
        DNSPacket.Flags flags = new DNSPacket.Flags();
        flags.setQR(DNSPacket.QR_CODE_QUERY);

        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setFlags(flags);
        dnsPacket.addQuestionRecord(question);

        byte[] packetData = null;
        try {
            packetData = dnsPacket.encodePacket();
        } catch (IOException exc) {
            Log.e("QueryThread", "query: Failed to encode question packet", exc);
            return;
        }

        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                MDNSManager.getMDNSAddress(), MDNSManager.MDNS_PORT);

        try {
            mSocket.send(packet);
        } catch (IOException exc) {
            Log.e("QueryThread", "query: Failed to send packet", exc);
            return;
        }
    }

    private void receive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mSocket.receive(packet);
        } catch (SocketTimeoutException timeoutExc) {
            // Timed out listening for incoming packets.
            Log.d("QueryThread", "receive: Timed out waiting for packets.");
            return;
        } catch (IOException exc) {
            Log.e("QueryThread", "receive: Failed to receive packet", exc);
            return;
        }

        byte[] packetData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        DNSPacket dnsPacket = new DNSPacket();
        try {
            dnsPacket.parsePacket(packetData);
        } catch (IOException exc) {
            Log.e("QueryThread", "Error parsing DNS packet", exc);
            return;
        }

        // Add the DNS packet to the MDNS cache.
        Log.e("QueryThread", "GOT PACKET: " + dnsPacket.toString());
        mCache.addDNSPacket(dnsPacket);
    }
}
