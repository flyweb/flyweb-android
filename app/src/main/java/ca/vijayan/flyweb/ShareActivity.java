package ca.vijayan.flyweb;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.TextView;
import ca.vijayan.flyweb.embedded_server.Common;
import ca.vijayan.flyweb.embedded_server.EmbeddedServer;
import ca.vijayan.flyweb.embedded_server.Strings;

import java.io.IOException;

public class ShareActivity extends AppCompatActivity {
    private final int FILE_CHOOSER_REQUEST_CODE = 1;

    private ProgressDialog mProgressDialog = null;
    private EmbeddedServer mServer = null;
    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;
    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;

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

            mServer = new EmbeddedServer(this);

            try {
                mServer.start();
            } catch (IOException e) {
                Log.e("ShareActivity", "Embedded server cannot be started.");
            }

            if (mServer != null) {
                registerService();
                setContentView(R.layout.activity_browse);
                ViewGroup group = (ViewGroup) findViewById(R.id.activity_browse);
                TextView titleView = (TextView) group.findViewById(R.id.browse_title);
                titleView.setText(mServer.getServiceName());
                mWebView = (WebView) group.findViewById(R.id.browse_webview);
                WebSettings settings = mWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);
                mWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        view.loadUrl(url);
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        if (url.equals(new StringBuilder(Common.LOCALHOST).append(mServer.getPort()).append(Common.
                                UPLOAD_ENDPOINT).toString())) {
                            mProgressDialog = new ProgressDialog(ShareActivity.this);
                            mProgressDialog.setMessage(Strings.UPLOADING);
                            mProgressDialog.setCanceledOnTouchOutside(false);
                            mProgressDialog.show();
                        }
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }
                    }
                });
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
                mWebView.loadUrl(Common.LOCALHOST + mServer.getPort());
            } else {
                setContentView(R.layout.activity_share);
                Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
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

    public void onHamburgerButtonClicked(View view) {
        super.onBackPressed();
        finish();
    }

    public void onReloadButtonClicked(View view) {
        mWebView.reload();
    }

    public void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(mServer.getServiceName());
        serviceInfo.setServiceType("_flyweb._tcp");
        serviceInfo.setPort(EmbeddedServer.DEFAULT_PORT);
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
