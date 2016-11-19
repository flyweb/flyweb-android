package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.io.IOException;

public class DiscoverActivity extends Activity implements Handler.Callback {

    static public final String EXTRA_SERVICE_INFO = "ca.vijayan.flyweb.SERVICE_INFO";

    static public final int MESSAGE_ADD_SERVICE = 0;
    static public final int MESSAGE_REMOVE_SERVICE = 1;

    Handler mHandler;
    DiscoverListAdapter mDiscoverListAdapter;
    DiscoveryManager mDiscoveryManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);

        mHandler = new Handler(this);

        mDiscoverListAdapter = new DiscoverListAdapter(this);
        try {
            mDiscoveryManager = new DiscoveryManager(this, mHandler);
            mDiscoveryManager.startDiscovery();
        } catch (IOException exc) {
            Log.e("DiscoverActivity", "Failed to instantiate Discovery Manager", exc);
        }

        ViewGroup layout = (ViewGroup) findViewById(R.id.activity_discover);
        ListView discoverListView = (ListView) layout.findViewById(R.id.discover_list);
        discoverListView.setAdapter(mDiscoverListAdapter);
    }

    public void onItemSelected(View target) {
        Intent intent = new Intent(this, BrowseActivity.class);
        NsdServiceInfo serviceInfo = (NsdServiceInfo) target.getTag();
        intent.putExtra(EXTRA_SERVICE_INFO, serviceInfo);
        startActivity(intent);
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message.what == MESSAGE_ADD_SERVICE) {
            NsdServiceInfo serviceInfo = (NsdServiceInfo) message.obj;
            mDiscoverListAdapter.addServiceInfo(serviceInfo);
            return true;
        } else if (message.what == MESSAGE_REMOVE_SERVICE) {
            NsdServiceInfo serviceInfo = (NsdServiceInfo) message.obj;
            mDiscoverListAdapter.removeServiceInfo(serviceInfo);
            return true;
        }
        return false;
    }
}
