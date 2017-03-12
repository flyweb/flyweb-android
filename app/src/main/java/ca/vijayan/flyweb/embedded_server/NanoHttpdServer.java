package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Irene Chen on 3/12/2017.
 */

public class NanoHttpdServer extends NanoHTTPD {
    private final static int DEFAULT_PORT = 8080;

    private Activity mActivity;
    private String directoryPath = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/Camera";
    private String testFilePath = "/test2.jpg";
    private boolean success;

    public NanoHttpdServer(Activity activity) {
        super(DEFAULT_PORT);
        mActivity = activity;
    }

    @Override
    public Response serve(IHTTPSession session) {
        return Method.POST.equals(session.getMethod()) ? post(session) : get();
    }

    public Response get() {
        return generateResponse("<form name='up' method='post' enctype='multipart/form-data'>"
                + "<input type='file' name='file' /><br />"
                + "<input type='submit'name='submit' value='Upload' />");
    }

    public Response post(IHTTPSession session) {
        success = false;
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (ResponseException | IOException e) {
            Log.e("NanoHttpdServer", "Error reading uploaded file.");
            return generateResponse("Error reading uploaded file.");
        }

        // TODO iterate over map for multiple files
        final String tempFilePath = files.get("file");
        if (tempFilePath == null || tempFilePath.isEmpty()) {
            return generateResponse("Please upload a valid file.");
        }

        final File outFile = new File(directoryPath + testFilePath);
        if (outFile.exists()) {
//            Runnable runnable = new Runnable() {
//                @Override
//                public void run() {
//                    new OverwriteDialog(tempFilePath, outFile).execute();
//                }
//            };
//            Thread t = new Thread(runnable);
//
//            mActivity.runOnUiThread(t);
            Thread t = new OverwriteDialogThread(tempFilePath, outFile);
            t.start();
            synchronized (t) {
                try {
                    Log.d("Server", "Waiting for input");
                    t.wait();
                } catch (InterruptedException e) {
                    Log.d("NanoHttpdServer", "Overwrite confirmation dialog interuppted.");
                }
                Log.d("Server", "Finished waiting");
            }
            Log.d("Server", "we here");
        } else {
            write(tempFilePath, outFile);
        }

        return success ? generateResponse("Successfully uploaded.") : generateResponse("Failed to upload file.");
    }

    private Response generateResponse(String content) {
        return newFixedLengthResponse(new StringBuilder("<html><body>").append(content).append("</body></html>")
                .toString());
    }

    private void write(String srcPath, File outFile) {
        try {
            File inFile = new File(srcPath);
            InputStream inStream = new FileInputStream(inFile);
            OutputStream outStream = new FileOutputStream(outFile);
            int bytesRead;
            while ((bytesRead = inStream.read()) > -1) {
                outStream.write(bytesRead);
            }
            inStream.close();
            outStream.close();
            success = true;
            Log.d("Server", "Finish write");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NanoHttpdServer", "Error writing out file when uploading");
        }
    }

    // TODO Method for helping me figure out what files are in the current directory; to be removed
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
    private class OverwriteDialogThread extends Thread {
        //private AlertDialog ad;
        private String srcPath;
        private File outFile;
        private Handler handler;

        public OverwriteDialogThread(final String srcPath, final File outFile) {
            this.srcPath = srcPath;
            this.outFile = outFile;
        }

        public void run() {
            Looper.prepare();

            handler = new Handler();
            new AlertDialog.Builder(mActivity)
                    .setTitle("File already exists")
                    .setMessage("Do you want to overwrite the existing file?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            write(srcPath, outFile);
                            Log.d("Server", "notifyThread");
                            notifyThread();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            notifyThread();
                        }
                    }).show();

            Looper.loop();
        }

        void notifyThread() {
            synchronized (this) {
                Log.d("Server", "Synced notify");
                notify();
            }
        }
    }
    private class OverwriteDialog extends AsyncTask<Void, Void, Void> {
        public OverwriteDialog(final String srcPath, final File outFile) {
            new AlertDialog.Builder(mActivity)
                    .setTitle("File already exists")
                    .setMessage("Do you want to overwrite the existing file?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            synchronized (this) {
                                dialog.dismiss();
                                Log.d("Server", "dismiss");
                                write(srcPath, outFile);
                                Log.d("Server", "notify");
                                notifyAll();
                            }
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            synchronized (this) {
                                dialog.dismiss();
                                Log.d("server", "notify");
                                notify();
                            }
                        }
                    }).show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            return null;
        }
    }
}