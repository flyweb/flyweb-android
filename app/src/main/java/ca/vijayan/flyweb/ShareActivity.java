package ca.vijayan.flyweb;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ShareActivity extends AppCompatActivity {
    private final static int DEFAULT_PORT = 8080;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    ProgressDialog progressDialog = null;
    NanoHttpdServer server = null;
    String directoryPath = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/Camera";
    String testFilePath = "/test2.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.upload);

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // TODO extract
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
        server = new NanoHttpdServer();
        try {
            server.start();
        } catch (IOException e) {
            Log.e("ShareActivity", "NanoHttpd server cannot be started");
        }

//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                progressDialog = ProgressDialog.show(ShareActivity.this, "", "Uploading File", true);
//
//                new Thread(new Runnable() {
//                    public void run() {
//                        runOnUiThread(new Runnable() {
//                            public void run() {
//                                upload(directoryPath);
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

    private class NanoHttpdServer extends NanoHTTPD {

        public NanoHttpdServer()
        {
            super(DEFAULT_PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String form = "<html><body>" +
                    "<form name='up' method='post' enctype='multipart/form-data'>"
                    + "<input type='file' name='file' /><br />"
                    + "<input type='submit'name='submit' value='Upload' />"
                    + "</body></html>";
            Map<String, String> headers = session.getHeaders();
            Map<String, String> parms = session.getParms();
            Method method = session.getMethod();
            String uri = session.getUri();
            Map<String, String> files = new HashMap<>();
            if (Method.POST.equals(method) || Method.PUT.equals(method)) {
                Log.d("ShareActivity", "Start post request");
                try {
                    session.parseBody(files);
                    // TODO iterate over map for multiple files
                    String tempFilePath = files.get("file");
                    if (tempFilePath == null) {
                        // TODO Response for invalid parameters
                    }
                    File outFile = new File(directoryPath + testFilePath);
                    if (outFile.exists()) {
                        // TODO confirm file overwrite
                        Log.d("ShareActivity", "Overwrite File");
                    }
                    File inFile = new File(tempFilePath);
                    listAllFiles();
                    InputStream inStream = new FileInputStream(inFile);
                    OutputStream outStream = new FileOutputStream(outFile);
                    int bytesRead;
                    while ((bytesRead = inStream.read()) > -1) {
                        outStream.write(bytesRead);
                    }
                    inStream.close();
                    outStream.close();
                } catch (Exception e) {
                    // TODO Handle Exception properly
                    Log.e("ShareActivity", "Error in parsing files");
                    return newFixedLengthResponse("<html><body>Failure</body></html>");
                }
                Log.d("ShareActivity", "Success?");
                return newFixedLengthResponse("<html><body>Success</body></html>");
            }
            Log.d("ShareActivity", "GET");
            return newFixedLengthResponse(form);
        }
    }

    private void listAllFiles() {
        Log.d("Files", "Path: " + directoryPath);
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();
        Log.d("Files", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            Log.d("Files", "FileName:" + files[i].getName());
        }
    }
}
