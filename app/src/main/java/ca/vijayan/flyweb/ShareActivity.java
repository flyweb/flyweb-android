package ca.vijayan.flyweb;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import ca.vijayan.flyweb.embedded_server.Common;
import ca.vijayan.flyweb.embedded_server.NanoHttpdServer;

import java.io.IOException;

public class ShareActivity extends AppCompatActivity {
    private ProgressDialog mProgressDialog = null;
    private NanoHttpdServer mServer = null;
    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Common.checkPermissions(this);

        if (!isExternalStorageWritable()) {
            Log.e("ShareActivity", "Cannot write to external storage");
            // TODO notify user external storage is not available even though we have the permissions
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.upload);
        mServer = new NanoHttpdServer(this);

        try {
            mServer.start();
            if (mServer != null) {
                registerService();
            }
        } catch (IOException e) {
            Log.e("ShareActivity", "NanoHttpd mServer cannot be started.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mServer != null) {
            mServer.stop();
            mNsdManager.unregisterService(mRegistrationListener);
        }
    }

    public void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(mServer.getServiceName());
        serviceInfo.setServiceType("_flyweb._tcp");
        serviceInfo.setPort(NanoHttpdServer.DEFAULT_PORT);
        initializeRegistrationListener();
        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("ShareActivity", "Failed to register embedded server.");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("ShareActivity", "Failed to unregister embedded server.");
            }
        };
    }
}
