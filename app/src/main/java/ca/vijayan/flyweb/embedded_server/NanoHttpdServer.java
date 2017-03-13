package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Irene Chen on 3/12/2017.
 */

public class NanoHttpdServer extends NanoHTTPD {
    private final static int DEFAULT_PORT = 8080;

    private Activity mActivity;
    private String directoryPath = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_PICTURES;
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
                + "<input type='submit'name='submit' value='" + Strings.UPLOAD + "' />");
    }

    public Response post(IHTTPSession session) {
        success = false;
        Map<String, String> files = new HashMap<>();
        Map<String, List<String>> params;

        try {
            session.parseBody(files);
            params = session.getParameters();
        } catch (ResponseException | IOException e) {
            Log.e("NanoHttpdServer", "Error reading uploaded file.");
            return generateResponse(Strings.ERROR_READING);
        }

        // TODO iterate over map for multiple files
        String tempFilePath = files.get("file");
        if (tempFilePath == null || tempFilePath.isEmpty()) {
            return generateResponse(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        List<String> fileParam = params.get("file");
        if (fileParam == null || fileParam.isEmpty()) {
            return generateResponse(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        // Since we are only dealing with a single upload at once for now, always get the first file name
        String originalFileName = fileParam.get(0);

        boolean dirExists = checkDir();

        if (dirExists) {
            File outFile = new File(directoryPath, originalFileName);
            if (outFile.exists()) {
                Thread t = new OverwriteDialogThread(tempFilePath, outFile);
                t.start();
                synchronized (t) {
                    try {
                        t.wait();
                    } catch (InterruptedException e) {
                        Log.e("NanoHttpdServer", "Overwrite confirmation dialog interrupted.");
                    }
                }
            } else {
                write(tempFilePath, outFile);
            }

            if (success) {
                return generateResponse(Strings.SUCCESSFULLY_UPLOADED);
            }
        }
        return generateResponse(Strings.FAILED_UPLOAD);
    }

    private Response generateResponse(String content) {
        return newFixedLengthResponse(new StringBuilder("<html><body>").append(content).append("</body></html>")
                .toString());
    }

    private boolean checkDir() {
        File dir = new File(directoryPath);
        boolean dirExists = dir.isDirectory();
        if (!dir.isDirectory()) {
            dirExists = dir.mkdir();
        }
        return dirExists;
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
            Log.e("NanoHttpdServer", "Error writing out file when uploading.");
        }
    }

    private class OverwriteDialogThread extends Thread {
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
                    .setTitle(Strings.FILE_EXISTS)
                    .setMessage(Strings.OVERWRITE_PERMISSION_REQUEST)
                    .setPositiveButton(Strings.YES, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            try {
                                outFile.delete();
                                outFile.createNewFile();
                            } catch (IOException e) {
                                Log.e("NanoHttpdServer", "Cannot overwrite and create new file.");
                                notifyThread();
                                return;
                            }
                            write(srcPath, outFile);
                            notifyThread();
                        }
                    })
                    .setNegativeButton(Strings.NO, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            notifyThread();
                        }
                    }).show();

            Looper.loop();
        }

        private void notifyThread() {
            synchronized (this) {
                notify();
            }
        }
    }
}