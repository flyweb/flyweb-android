package ca.vijayan.flyweb.utils;

import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * Created by Irene Chen on 3/28/2017.
 */

public class Common {
    public static void configureWebSettings(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
    }
}
