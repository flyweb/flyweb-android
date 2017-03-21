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
    private DNSServiceInfo mServiceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        boolean havePermissions = Common.checkPermissions(this);
	   
	   if (havePermissions) {
        if (!isExternalStorageWritable()) {
            Log.e("ShareActivity", "Cannot write to external storage");
            // TODO notify user external storage is not available even though we have permissions
        }

                mServer = new NanoHttpdServer(this);

        try {
            mServer.start();
        } catch (IOException e) {
            Log.e("ShareActivity", "NanoHttpd mServer cannot be started.");
        }

		if (mServer != null) {
                registerService();
			setContentView(R.layout.activity_browse);
			//mServiceInfo = TODO
			ViewGroup group = (ViewGroup) 							findViewById(R.id.activity_browse);
        		TextView titleView = (TextView) 							group.findViewById(R.id.browse_title);
// TODO
        		titleView.setText("File shared by: ");
mWebView = (WebView) group.findViewById(R.id.browse_webview);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             WebChromeClient.FileChooserParams fileChooserParams)
            {
                Intent intent = fileChooserParams.createIntent();
                mFilePathCallback = filePathCallback;
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });
// OTOD remove this hard code
mWebView.loadUrl("http://localhost:8080");

            } else {
			setContentView(R.layout.activity_share);
			Toolbar toolbar = (Toolbar) 							findViewById(R.id.toolbar);
        		setSupportActionBar(toolbar);
		}
	}
    }

@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            mFilePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            mFilePathCallback = null;
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
