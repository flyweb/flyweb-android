package ca.vijayan.flyweb;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class ShareActivity extends AppCompatActivity {
    final static int DEFAULT_PORT = 8080;

    ProgressDialog progressDialog = null;
    NanoHttpdServer server = null;
    String testFilePath = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/" + "Camera/test.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.upload);
        server = new NanoHttpdServer();
        try {
            server.start();
        } catch (IOException e) {
            Log.e("ShareActivity", "NanoHttpd server cannot be started");
        }
        Log.d("ShareActivity", "Server initialized");
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                progressDialog = ProgressDialog.show(ShareActivity.this, "", "Uploading File", true);
//
//                new Thread(new Runnable() {
//                    public void run() {
//                        runOnUiThread(new Runnable() {
//                            public void run() {
//                                upload(testFilePath);
//                            }
//                        });
//                    }
//                }).start();
//            }
//        });
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (server != null)
            server.stop();
    }

    private void upload(String path) {
        Log.d("ShareActivity", "Start upload");
        File file = new File(path);

        if (!file.isFile()) {
            progressDialog.dismiss();
            Log.e("ShareActivity", "Source File not exist :" + path);
        } else {
            Log.d("ShareActivity", "We received a file!");
        }
    }

    private class NanoHttpdServer extends NanoHTTPD {

        public NanoHttpdServer()
        {
            super(DEFAULT_PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
//            String answer = "";
//            try {
//                // Open file from SD Card
//                File root = Environment.getExternalStorageDirectory();
//                FileReader index = new FileReader(root.getAbsolutePath() +
//                        "/www/index.html");
//                BufferedReader reader = new BufferedReader(index);
//                String line = "";
//                while ((line = reader.readLine()) != null) {
//                    answer += line;
//                }
//                reader.close();
//
//            } catch(IOException ioe) {
//                Log.w("Httpd", ioe.toString());
//            }
            String get = "<html><body><form name='up' method='post' enctype='multipart/form-data'>"
                    + "<input type='file' name='file' /><br /><input type='submit'name='submit' "
                    + "value='Upload'/></form></body></html>";

            return newFixedLengthResponse(get);
        }
    }


//    private boolean upload() {
//        Log.d("ShareActivity", "Start upload");
//        try {
//            ExecutorService executor = Executors.newFixedThreadPool(1);
//            Future<Boolean> future = executor.submit(new Callable<Boolean>() {
//                @Override
//                public Boolean call() {
//                    try {
//                        Thread.sleep(2000);
//                        return true;
//                    } catch (Exception e) {
//                        return false;
//                    }
//                }
//            });
//            boolean success = future.get();
//            if (success) {
//                Log.d("ShareActivity", "Upload successful!");
//            } else {
//                Log.d("ShareActivity", "Upload failed");
//            }
//        } catch (Exception e) {
//            Log.e("ShareActivity", "error");
//        }
//        return true;
//    }
}
