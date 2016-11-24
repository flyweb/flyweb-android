package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * Created by Kannan Vijayan on 11/16/2016.
 */

public class PassiveThread extends Thread {
    private MulticastSocket mPassiveSocket;
    private MDNSCache mCache;
    private boolean mStop;
    private boolean mPaused;

    private static final int RECV_TIMEOUT = 100;

    public static final String logName() {
        return "PassiveThread";
    }

    public PassiveThread(MulticastSocket socket, MDNSCache cache)
            throws IOException
    {
        super();
        mPassiveSocket = socket;
        mPassiveSocket.setSoTimeout(RECV_TIMEOUT);
        mCache = cache;
        mStop = false;
        mPaused = false;
    }

    // NOTE: This will always be called from a separate thread.
    public void shutdown() {
        synchronized (this) {
            mStop = true;
        }
        mPassiveSocket.close();

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

    @Override
    public void run() {
        for (;;) {
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
                        continue;
                    }
                }
            }
            passiveReceive();
        }
    }

    void passiveReceive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mPassiveSocket.receive(packet);
        } catch (SocketTimeoutException exc) {
            return;
        } catch (IOException exc) {
            Log.e(logName(), "poll: Failed to receive passive packet", exc);
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
