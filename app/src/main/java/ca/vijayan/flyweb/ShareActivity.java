package ca.vijayan.flyweb;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import ca.vijayan.flyweb.embedded_server.NanoHttpdServer;

import java.io.IOException;

public class ShareActivity extends AppCompatActivity {
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private ProgressDialog progressDialog = null;
    private NanoHttpdServer server = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkPermissions();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.upload);
        server = new NanoHttpdServer(this);

        try {
            server.start();
        } catch (IOException e) {
            Log.e("ShareActivity", "NanoHttpd server cannot be started.");
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }



    private void checkPermissions() {
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}
