package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

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
            Callable<Void> callable = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    new OverwriteDialog(tempFilePath, outFile).execute();
                    return null;
                }
            };
            FutureTask<Void> task = new FutureTask<>(callable);
            mActivity.runOnUiThread(task);
            try {
                Log.d("Server", "trying");
                task.get();
            } catch (Exception e) {
                Log.e("Exception", "hteehee");
                e.printStackTrace();
            }
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
        } catch (Exception e) {
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

    private class OverwriteDialog extends AsyncTask<Void, Void, Void> {
        public OverwriteDialog(final String srcPath, final File outFile) {
            new AlertDialog.Builder(mActivity)
                    .setTitle("File already exists")
                    .setMessage("Do you want to overwrite the existing file?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            write(srcPath, outFile);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            return null;
        }
    }
}