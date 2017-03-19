package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;

/**
 * Created by karui on 3/18/2017.
 */

public class DownloadHelper {
    private HttpURLConnection mConn;
    private Activity mActivity;

    public DownloadHelper(HttpURLConnection conn, Activity activity) {
        mConn = conn;
        mActivity = activity;
        Common.checkPermissions(activity);
        download();
    }

    private void download() {
        String originalFileName = mConn.getHeaderField(Common.HEADER_FILENAME_KEY);

        boolean dirExists = checkDir();

        File outFile = new File(Common.DIRECTORY_PATH, originalFileName);
        if (dirExists) {
            if (outFile.exists()) {
                Thread t = new DownloadHelper.OverwriteDialogThread(outFile);
                t.start();
                synchronized (t) {
                    try {
                        t.wait();
                    } catch (InterruptedException e) {
                        Log.e("NanoHttpdServer", "Overwrite confirmation dialog interrupted.");
                    }
                }
            } else {
                read(outFile);
            }
        }

        DownloadManager downloadManager = (DownloadManager) mActivity.getSystemService(mActivity.DOWNLOAD_SERVICE);

        downloadManager.addCompletedDownload(originalFileName, originalFileName, true, Common.getMimeType(Uri.
                 fromFile(outFile).toString()), outFile.getAbsolutePath(), outFile.length(), true);

    }

    private boolean checkDir() {
        File dir = new File(Common.DIRECTORY_PATH.toString());
        boolean dirExists = dir.isDirectory();
        if (!dir.isDirectory()) {
            dirExists = dir.mkdir();
        }
        return dirExists;
    }

    private void read(File outFile) {
        try {
            InputStream inStream = mConn.getInputStream();
            OutputStream outStream = new FileOutputStream(outFile);
            int bytesRead;
            while ((bytesRead = inStream.read()) > -1) {
                outStream.write(bytesRead);
            }
            inStream.close();
            outStream.close();
        } catch (IOException e) {
            Log.e("DownloadHelper", "Error reading in file response");
        }
    }

    private class OverwriteDialogThread extends Thread {
        private File outFile;
        private Handler handler;

        public OverwriteDialogThread(final File outFile) {
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
                            read(outFile);
                            notifyThread();
                        }
                    })
                    .setNegativeButton(Strings.NO, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            notifyThread();
                        }
                    })
                    .setCancelable(false)
                    .show();

            Looper.loop();
        }

        private void notifyThread() {
            synchronized (this) {
                notify();
            }
        }
    }
}
