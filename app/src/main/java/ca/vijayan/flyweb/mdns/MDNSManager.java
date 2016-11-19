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

public class MDNSManager implements MDNSCache.Listener {
    public static byte[] MDNS_ADDRESS_BYTES = new byte[] { (byte)224, 0, 0, (byte)251 };
    public static InetAddress getMDNSAddress() {
        try {
            return Inet4Address.getByAddress(MDNS_ADDRESS_BYTES);
        } catch (UnknownHostException exc) {
            Log.e("MDNSManager", "getMDNSAddress failed (SHOULD NOT HAPPEN)");
            return null;
        }
    }
    public static int MDNS_PORT = 5353;

    QueryThread mQueryThread;
    PassiveThread mPassiveThread;
    MDNSCache mCache;
    MulticastSocket mPassiveSocket;
    DatagramSocket mQuerySocket;

    public MDNSManager() throws IOException {
        mPassiveSocket = new MulticastSocket(MDNS_PORT);
        mPassiveSocket.joinGroup(getMDNSAddress());
        mQuerySocket = new DatagramSocket();
        mCache = new MDNSCache();

        mQueryThread = new QueryThread(mQuerySocket, mCache);
        mPassiveThread = new PassiveThread(mPassiveSocket, mCache);
    }

    public void start() {
        mCache.start();
        mQueryThread.start();
        mPassiveThread.start();
    }

    synchronized public void shutdown() {
        mCache.shutdown();
        mPassiveThread.shutdown();
        mQueryThread.shutdown();
        mPassiveSocket.close();
        mQuerySocket.close();
    }

    // Listener methods for service events
    @Override
    public void onDNSServiceFound(DNSServiceInfo info) {
        Log.d("MDNSManager", "onDNSServiceFound: " + info.toString());
    }
    @Override
    public void onDNSServiceLost(DNSServiceInfo info) {
        Log.d("MDNSManager", "onDNSServiceLost: " + info.toString());
    }
    @Override
    public void onDNSServiceChanged(DNSServiceInfo info, DNSServiceInfo oldInfo) {
        Log.d("MDNSManager", "onDNSServiceChanged: " + info.toString());
    }
}
