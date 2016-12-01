package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.net.InetAddress;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;

public class BrowseActivity extends Activity {

    DNSServiceInfo mServiceInfo;
    WebView mWebView;
    CookieManager mCookieManager;
    ValueCallback<Uri[]> mFilePathCallback;

    static final String MOBILE_UA =
        "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) " +
                "AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1\n";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1;
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
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        mWebView.setWebViewClient(new WebViewClient() {
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
        mCookieManager = CookieManager.getInstance();
        mCookieManager.setAcceptCookie(true);
        mCookieManager.setAcceptThirdPartyCookies(mWebView, false);
        mWebView.loadUrl(mServiceInfo.getServiceURL());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            mFilePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            mFilePathCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        // Clear the webview cache.
        mWebView.clearCache(true);
        mCookieManager.removeAllCookies(null);
        super.onDestroy();
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
