package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.net.InetAddress;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;

public class BrowseActivity extends Activity {

    WebView mWebView;
    DNSServiceInfo mServiceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        Intent intent = getIntent();
        mServiceInfo = intent.getParcelableExtra(DiscoverActivity.EXTRA_SERVICE_INFO);

        ViewGroup group = (ViewGroup) findViewById(R.id.activity_browse);

        TextView titleView = (TextView) group.findViewById(R.id.browse_title);
        titleView.setText(mServiceInfo.displayName());

        mWebView = (WebView) group.findViewById(R.id.browse_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
        });
        mWebView.loadUrl(mServiceInfo.getServiceURL());
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

    public void onHamburgerButtonClicked(View view) {
        super.onBackPressed();
        finish();
    }

    public void onReloadButtonClicked(View view) {
        mWebView.reload();
    }
}
