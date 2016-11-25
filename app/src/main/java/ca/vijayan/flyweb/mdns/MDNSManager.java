package ca.vijayan.flyweb.mdns;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

import ca.vijayan.flyweb.DiscoverActivity;

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
    Handler mServiceInfoHandler;

    public MDNSManager(Handler serviceInfoHandler) throws IOException {
        mServiceInfoHandler = serviceInfoHandler;

        mPassiveSocket = new MulticastSocket(MDNS_PORT);
        mPassiveSocket.joinGroup(getMDNSAddress());
        mQuerySocket = new DatagramSocket();
        mCache = new MDNSCache();

        mQueryThread = new QueryThread(mQuerySocket, mCache);
        mPassiveThread = new PassiveThread(mPassiveSocket, mCache);
    }

    public void start() {
        mCache.start();
        mCache.addListener(this);
        mQueryThread.start();
        mPassiveThread.start();
    }

    public void pause() {
        mQueryThread.pause();
        mPassiveThread.pause();
        mCache.pause();
    }

    public void unpause() {
        mCache.unpause();
        mPassiveThread.unpause();
        mQueryThread.unpause();
    }

    synchronized public void shutdown() {
        mCache.removeListener(this);
        mCache.shutdown();
        mPassiveThread.shutdown();
        mQueryThread.shutdown();
        mPassiveSocket.close();
        mQuerySocket.close();
    }

    // Listener methods for service events
    @Override
    public void onDNSServiceFound(DNSServiceInfo info) {
        Log.d("MDNSManager", "onDNSServiceFound: HEREHEREHERE!!!!!!!!");
        Log.d("MDNSManager", "onDNSServiceFound: " + info.toString());
        Log.d("MDNSManager", "onDNSServiceFound: HEREHEREHERE!!!!!!!!");
        if (info.getType().equals(PacketEncoder.dottedToName("_http._tcp.local")) ||
            info.getType().equals(PacketEncoder.dottedToName("_flyweb._tcp.local")))
        {
            Message m = mServiceInfoHandler.obtainMessage(DiscoverActivity.MESSAGE_ADD_SERVICE,
                    info.clone());
            m.sendToTarget();
        }
    }
    @Override
    public void onDNSServiceLost(DNSServiceInfo info) {
        Log.d("MDNSManager", "onDNSServiceLost: HEREHEREHERE!!!!!!!!");
        Log.d("MDNSManager", "onDNSServiceLost: " + info.toString());
        Log.d("MDNSManager", "onDNSServiceLost: HEREHEREHERE!!!!!!!!");
        if (info.getType().equals(PacketEncoder.dottedToName("_http._tcp.local")) ||
            info.getType().equals(PacketEncoder.dottedToName("_flyweb._tcp.local")))
        {
            Message m = mServiceInfoHandler.obtainMessage(DiscoverActivity.MESSAGE_REMOVE_SERVICE,
                    info.clone());
            m.sendToTarget();
        }
    }
    @Override
    public void onDNSServiceChanged(DNSServiceInfo info, DNSServiceInfo oldInfo) {
        Log.d("MDNSManager", "onDNSServiceChanged: HEREHEREHERE!!!!!!!!");
        if (info.getType().equals(PacketEncoder.dottedToName("_http._tcp.local")) ||
            info.getType().equals(PacketEncoder.dottedToName("_flyweb._tcp.local")))
        {
            Message m = mServiceInfoHandler.obtainMessage(DiscoverActivity.MESSAGE_UPDATE_SERVICE,
                    new Pair<DNSServiceInfo, DNSServiceInfo>(info.clone(), oldInfo.clone()));
            m.sendToTarget();
        }
    }
}
