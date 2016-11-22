package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;

public class BrowseActivity extends Activity {

    WebView mWebView;
    DNSServiceInfo mServiceInfo;
    UUID mUUID;

    private String makeServiceBaseUrl() {
        return "fly://" + mUUID.toString() + ".uuid/";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        Intent intent = getIntent();
        mServiceInfo = intent.getParcelableExtra(DiscoverActivity.EXTRA_SERVICE_INFO);

        mUUID = UUID.randomUUID();

        ViewGroup group = (ViewGroup) findViewById(R.id.activity_browse);
        mWebView = (WebView) group.findViewById(R.id.browse_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String response = "<html><head><title>Hello</title></head>" +
                        "<body><h1>HELLO</h1><a href=\"http://yahoo.com\">yahoo</a>" +
                        "<p>" + req.getUrl().toString() + "</p>" +
                        "</body></html>";
                byte[] data;
                try {
                    data = response.getBytes("UTF-8");
                } catch (UnsupportedEncodingException exc) {
                    throw new RuntimeException(exc);
                }
                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(data));
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (req.getUrl().getScheme().equals("fly")) {
                    return true;
                } else {
                    view.getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, req.getUrl()));
                    return true;
                }
            }
        });
        mWebView.loadUrl(makeServiceBaseUrl());
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
