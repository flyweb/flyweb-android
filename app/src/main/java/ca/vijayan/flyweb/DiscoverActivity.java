package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;
import ca.vijayan.flyweb.embedded_server.Common;
import ca.vijayan.flyweb.embedded_server.DownloadHelper;
import ca.vijayan.flyweb.embedded_server.Strings;
import ca.vijayan.flyweb.mdns.DNSServiceInfo;
import ca.vijayan.flyweb.mdns.MDNSManager;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DiscoverActivity extends Activity implements Handler.Callback {

    static public final String EXTRA_SERVICE_INFO = "ca.vijayan.flyweb.SERVICE_INFO";

    static public final int MESSAGE_ADD_SERVICE = 0;
    static public final int MESSAGE_REMOVE_SERVICE = 1;
    static public final int MESSAGE_UPDATE_SERVICE = 2;

    static private final int TIMEOUT_IN_MILLIS = 30000;

    Handler mHandler;
    DiscoverListAdapter mDiscoverListAdapter;
    MDNSManager mMDNSManager;
    List<File> mFilesToDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);

        mHandler = new Handler(this);
        mDiscoverListAdapter = new DiscoverListAdapter(this);
        mFilesToDownload = new ArrayList<>();

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

    @Override
    protected void onPause() {
        mMDNSManager.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mMDNSManager.unpause();
        super.onResume();
    }

    public void onItemSelected(View target) {
        mFilesToDownload = new ArrayList<>();
        if (isEmbeddedServer((DNSServiceInfo) target.getTag(), this)) {
            // TODO
        } else {
            Intent intent = new Intent(this, BrowseActivity.class);
            DNSServiceInfo serviceInfo = (DNSServiceInfo) target.getTag();
            intent.putExtra(EXTRA_SERVICE_INFO, serviceInfo);
            startActivity(intent);
        }
    }

    public void onShareButtonSelected(View target) {
        Intent intent = new Intent(this, ShareActivity.class);
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

    private boolean isEmbeddedServer(DNSServiceInfo dnsServiceInfo, final Activity activity) {
        AsyncTask<DNSServiceInfo, Void, Boolean> task = getTaskForIsEmbeddedServer(activity);

        boolean res = false;
        try {
            res = task.execute(dnsServiceInfo).get(TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            Log.d("DiscoverActivity", "Failed to get headers from URL connection.");
        } catch (TimeoutException e) {
            Log.d("DiscoverActivity", "Timed out while trying to check headers.");
        }
        return res;
    }

    private AsyncTask<DNSServiceInfo, Void, Boolean> getTaskForIsEmbeddedServer(final Activity activity) {
        return new AsyncTask<DNSServiceInfo, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(DNSServiceInfo... dnsServiceInfo1) {
                try {
                    URL object = new URL(dnsServiceInfo1[0].getBaseURL());
                    HttpURLConnection connection = (HttpURLConnection) object.openConnection();
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        return hasFlywebHeader(connection, activity);
                    }
                    return false;
                } catch (IOException e) {
                    Log.e("DiscoverActivity", "Failed to open URL connection.");
                    return false;
                }
            }
        };
    }

    private boolean hasFlywebHeader(HttpURLConnection connection, Activity activity) {
        String header = connection.getHeaderField(Common.FLYWEB_HEADER);
        if (header == null) {
            return false;
        }

        if (header.equals("false")) {
            if (Common.checkPermissions(this)) {
			handleToast(Strings.DOWNLOAD_STARTED);
                boolean downloadSuccess = new DownloadHelper(connection, activity).download();
                if (downloadSuccess) {
                    handleToast(Strings.DOWNLOAD_SUCCESSFUL);
                } else {
                    handleToast(Strings.DOWNLOAD_UNSUCCESSFUL);
                }
            }
        } else {
            handleToast(Strings.NO_FILES_AVAILABLE);
        }
        return true;
    }

    private void handleToast(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
