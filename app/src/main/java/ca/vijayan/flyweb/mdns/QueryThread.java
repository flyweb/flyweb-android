package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
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
    boolean mPaused;
    Date mLastQueryTime;

    private static final int RECV_TIMEOUT = 100;

    public static String logName() {
        return "QueryThread";
    }

    public QueryThread(DatagramSocket socket, MDNSCache cache)
        throws IOException
    {
        super();
        mSocket = socket;
        mSocket.setSoTimeout(RECV_TIMEOUT);
        mQueue = new ConcurrentLinkedQueue<>();
        mCache = cache;
        mStop = false;
        mPaused = false;
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

    public void pause() {
        Log.d(logName(), "Requesting pause.");
        synchronized (this) {
            mPaused = true;
        }
    }

    public void unpause() {
        Log.d(logName(), "Requesting unpause.");
        synchronized (this) {
            mPaused = false;
            notify();
        }
    }

    private static final int ISSUE_QUERY_INTERVAL = 5000;

    @Override
    public void run() {
        mLastQueryTime = new Date();
        mQueue.add(makeFlyWebQuestion());

        TOP: for (;;) {
            // Check for stop.
            synchronized (this) {
                if (mStop) {
                    break;
                }

                // wait on thread if paused.
                while (mPaused) {
                    Log.d(logName(), "Paused.");
                    try {
                        wait();
                    } catch (InterruptedException exc) {
                        if (!mPaused) {
                            Log.d(logName(), "Unpaused.");
                        }
                    }
                }
            }

            // Add a new FlyWeb query to queue if enough time has passed since last one.
            Date nowTime = new Date();
            if ((nowTime.getTime() - mLastQueryTime.getTime()) >= ISSUE_QUERY_INTERVAL) {
                mLastQueryTime = nowTime;
                mQueue.add(makeFlyWebQuestion());
                mQueue.add(makeHttpQuestion());
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
        List<String> name = DNSPacket.dottedToName("_flyweb._tcp.local");
        return new QuestionRecord(
                /* name = */ name,
                /* recordType = */ DNSPacket.RECORD_TYPE_PTR,
                /* classCode = */ DNSPacket.CLASS_CODE_IN,
                /* cacheFlush = */ true);
    }

    private static QuestionRecord makeHttpQuestion() {
        // Create a question for '_flyweb._tcp'.
        List<String> name = DNSPacket.dottedToName("_http._tcp.local");
        return new QuestionRecord(
                /* name = */ name,
                /* recordType = */ DNSPacket.RECORD_TYPE_PTR,
                /* classCode = */ DNSPacket.CLASS_CODE_IN,
                /* cacheFlush = */ true);
    }

    private void query(QuestionRecord question) {
        Log.e(logName(), "query: " + question.toString());
        DNSPacket.Flags flags = new DNSPacket.Flags();
        flags.setQR(DNSPacket.QR_CODE_QUERY);

        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setFlags(flags);
        dnsPacket.addQuestionRecord(question);

        byte[] packetData = null;
        try {
            packetData = dnsPacket.encodePacket();
        } catch (IOException exc) {
            Log.e(logName(), "query: Failed to encode question packet", exc);
            return;
        }

        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                MDNSManager.getMDNSAddress(), MDNSManager.MDNS_PORT);

        try {
            mSocket.send(packet);
        } catch (IOException exc) {
            Log.e(logName(), "query: Failed to send packet", exc);
            return;
        }
    }

    private void receive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mSocket.receive(packet);
        } catch (SocketTimeoutException timeoutExc) {
            return;
        } catch (IOException exc) {
            Log.e(logName(), "receive: Failed to receive packet", exc);
            return;
        }

        byte[] packetData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        DNSPacket dnsPacket = new DNSPacket();
        try {
            dnsPacket.parsePacket(packetData);
        } catch (IOException exc) {
            Log.e(logName(), "Error parsing DNS packet", exc);
            return;
        }

        // Add the DNS packet to the MDNS cache.
        mCache.addDNSPacket(dnsPacket);
    }
}
