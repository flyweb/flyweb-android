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
import android.webkit.MimeTypeMap;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Irene Chen on 3/12/2017.
 */

public class NanoHttpdServer extends NanoHTTPD {
    private static final int DEFAULT_PORT = 8080;
    private final String NANOHTTPD_KEY = "file";
	private final File directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
	
    private Activity mActivity;
    private boolean success;
	private Map<String, File> fileMap;

    public NanoHttpdServer(Activity activity) {
        super(DEFAULT_PORT);
        mActivity = activity;
		fileMap = new HashMap<>();
    }

    @Override
    public Response serve(IHTTPSession session) {
        return Method.POST.equals(session.getMethod()) ? post(session) : get();
    }

    public Response get() {
		if (fileMap.isEmpty()) {
			return generateResponse("<form name='up' method='post' enctype='multipart/form-data'>"
					+ "<input type='file' name='file' /><br />"
					+ "<input type='submit'name='submit' value='" + Strings.UPLOAD + "' />");
		}
		
		try {
			Iterator<File> it = map.values();
			DownloadManager downloadManager = (DownloadManager) mActivity.getSystemService(
							mActivity.DOWNLOAD_SERVICE);
			while (it.hasNext()) {
					File file = it.next();
					String originalFileName = file.getName();
					downloadManager.addCompletedDownload(originalFileName, originalFileName, true,
							getMimeType(Uri.fromFile(file).toString()), file.getAbsolutePath(), file.length(), true);
			}
			return generateResponse(Strings.DOWNLOAD_COMPLETE);
		} catch (Exception e) {
			Log.e("NanoHttpdServer", "Error download files.");
		}
		return generatedResponse("Strings.FAILED_DOWNLOAD");
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
        String tempFilePath = files.get(NANOHTTPD_KEY);
        if (tempFilePath == null || tempFilePath.isEmpty()) {
            return generateResponse(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        List<String> fileParam = params.get(NANOHTTPD_KEY);
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
        File dir = new File(directoryPath.toString());
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
			fileMap.put(UUID.randomUUID().toString(), outFile);
            inStream.close();
            outStream.close();
            success = true;
        } catch (Exception e) {
            Log.e("NanoHttpdServer", "Error writing out file when uploading.");
        }
    }

    private String getMimeType(String uri) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri);
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }
        return type;
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