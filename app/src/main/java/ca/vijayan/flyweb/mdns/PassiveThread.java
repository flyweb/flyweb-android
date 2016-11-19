package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Created by Kannan Vijayan on 11/16/2016.
 */

public class PassiveThread extends Thread {
    private MulticastSocket mPassiveSocket;
    private MDNSCache mCache;
    private boolean mStop;

    public PassiveThread(MulticastSocket socket, MDNSCache cache) {
        super();
        mPassiveSocket = socket;
        mCache = cache;
        mStop = false;
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

    @Override
    public void run() {
        for (;;) {
            // Check for stop.
            synchronized (this) {
                if (mStop) {
                    break;
                }
            }

            // passiveReceive();

            synchronized (this) {
                try {
                    this.wait(1000);
                } catch (InterruptedException exc) {
                    // Pass.
                }
            }
        }
    }

    void passiveReceive() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        try {
            mPassiveSocket.receive(packet);
        } catch (IOException exc) {
            Log.e("PassiveThread", "poll: Failed to receive passive packet", exc);
            return;
        }

        byte[] packetData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        DNSPacket dnsPacket = new DNSPacket();
        try {
            dnsPacket.parsePacket(packetData);
        } catch (IOException exc) {
            Log.e("PassiveThread", "Error parsing DNS packet", exc);
            return;
        }

        // Add the DNS packet to the MDNS cache.
        Log.e("PassiveThread", "GOT PACKET: " + dnsPacket.toString());
        mCache.addDNSPacket(dnsPacket);
    }
}
