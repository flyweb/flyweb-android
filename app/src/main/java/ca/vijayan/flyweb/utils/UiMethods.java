package ca.vijayan.flyweb.utils;

import android.app.Activity;
import android.widget.Toast;

/**
 * Created by Irene Chen on 3/23/2017.
 */

public class UiMethods {
    public static void handleToast(final String text, final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
