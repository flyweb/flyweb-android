package ca.vijayan.flyweb;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ShareActivity extends AppCompatActivity {
    ProgressDialog dialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.upload);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = ProgressDialog.show(ShareActivity.this, "", "Uploading File", true);

                new Thread(new Runnable() {
                    public void run() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                upload();
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private boolean upload() {
        Log.d("ShareActivity", "Start upload");
        try {
            ExecutorService executor = Executors.newFixedThreadPool(1);
            Future<Boolean> future = executor.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    try {
                        Thread.sleep(2000);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            });
            boolean success = future.get();
            if (success) {
                Log.d("ShareActivity", "Upload successful!");
            } else {
                Log.d("ShareActivity", "Upload failed");
            }
        } catch (Exception e) {
            Log.e("ShareActivity", "error");
        }
        return true;
    }
}
