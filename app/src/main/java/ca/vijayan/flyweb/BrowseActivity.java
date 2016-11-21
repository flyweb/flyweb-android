package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.InetAddress;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;

public class BrowseActivity extends Activity {

    WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        Intent intent = getIntent();
        DNSServiceInfo serviceInfo =
                intent.getParcelableExtra(DiscoverActivity.EXTRA_SERVICE_INFO);

        InetAddress addr = serviceInfo.getAddress();
        int port = serviceInfo.getPort();

        String url = "http://" + addr.getHostAddress();
        if (port != 80) {
            url += ":" + port;
        }

        ViewGroup group = (ViewGroup) findViewById(R.id.activity_browse);
        mWebView = (WebView) group.findViewById(R.id.browse_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
        });
        mWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (mWebView.isFocused() && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
            finish();
        }
    }
}
