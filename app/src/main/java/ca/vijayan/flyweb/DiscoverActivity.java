package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;
import ca.vijayan.flyweb.mdns.MDNSManager;

public class DiscoverActivity extends Activity implements Handler.Callback {

    static public final String EXTRA_SERVICE_INFO = "ca.vijayan.flyweb.SERVICE_INFO";

    static public final int MESSAGE_ADD_SERVICE = 0;
    static public final int MESSAGE_REMOVE_SERVICE = 1;
    static public final int MESSAGE_UPDATE_SERVICE = 2;

    Handler mHandler;
    DiscoverListAdapter mDiscoverListAdapter;
    MDNSManager mMDNSManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);

        mHandler = new Handler(this);
        mDiscoverListAdapter = new DiscoverListAdapter(this);

        try {
            mMDNSManager = new MDNSManager(mHandler);
            mMDNSManager.start();
        } catch (IOException exc) {
            Log.e("DiscoverActivity", "Failed to instantiate MDNSManager.", exc);
        }

        ViewGroup layout = (ViewGroup) findViewById(R.id.activity_discover);
        ListView discoverListView = (ListView) layout.findViewById(R.id.discover_list);
        discoverListView.setAdapter(mDiscoverListAdapter);
    }

    public void onItemSelected(View target) {
        Intent intent = new Intent(this, BrowseActivity.class);
        DNSServiceInfo serviceInfo = (DNSServiceInfo) target.getTag();
        intent.putExtra(EXTRA_SERVICE_INFO, serviceInfo);
        startActivity(intent);
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message.what == MESSAGE_ADD_SERVICE) {
            Log.d("DiscoverActivity", "Got MESSAGE_ADD_SERVICE");
            DNSServiceInfo serviceInfo = (DNSServiceInfo) message.obj;
            mDiscoverListAdapter.addServiceInfo(serviceInfo);
            return true;
        } else if (message.what == MESSAGE_REMOVE_SERVICE) {
            Log.d("DiscoverActivity", "Got MESSAGE_REMOVE_SERVICE");
            DNSServiceInfo serviceInfo = (DNSServiceInfo) message.obj;
            mDiscoverListAdapter.removeServiceInfo(serviceInfo);
            return true;
        } else if (message.what == MESSAGE_UPDATE_SERVICE) {
            Log.d("DiscoverActivity", "Got MESSAGE_UPDATE_SERVICE");
            Pair<DNSServiceInfo, DNSServiceInfo> serviceInfo =
                    (Pair<DNSServiceInfo, DNSServiceInfo>) message.obj;
            mDiscoverListAdapter.updateServiceInfo(serviceInfo.first, serviceInfo.second);
            return true;
        }
        return false;
    }
}
