package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.*;

/**
 * Created by Irene Chen on 3/12/2017.
 */

public class NanoHttpdServer extends NanoHTTPD {
    public  static final int DEFAULT_PORT = 8080; // TODO make port allocation dynamic
    private final String NANOHTTPD_KEY = "file";

    private int port;
    private Activity mActivity;
    private boolean mSuccess;
    private List<File> mFiles;
    private String mServiceName = "Flyweb File Sharing"; // TODO change service name based on device name

    public NanoHttpdServer(Activity activity) {
        super(DEFAULT_PORT);
        port = DEFAULT_PORT;
        mActivity = activity;
        mFiles = new ArrayList<>();
    }

    @Override
    public Response serve(IHTTPSession session) {
        return Method.POST.equals(session.getMethod()) ? post(session) : get();
    }

    public Response get() {
        Response response = null;
        if (mFiles.isEmpty()) {
            response = generateHtmlResponse("<form name='up' method='post' enctype='multipart/form-data'>"
                    + "<input type='file' name='file' /><br />"
                    + "<input type='submit'name='submit' value='" + Strings.UPLOAD + "' />");
        } else {
            try {
                for (File file : mFiles) { // there should only be one element in the map right now; return on first loop
                    FileInputStream fis = new FileInputStream(file);
                    response = newChunkedResponse(Response.Status.OK, Common.getMimeType(Uri.fromFile(file).toString()),
                            fis);
                    response.addHeader(Common.HEADER_FILENAME_KEY, file.getName());
                }
            } catch (Exception e) {
                Log.e("NanoHttpdServer", "Error download files.");
                response = generateHtmlResponse(Strings.DOWNLOAD_UNSUCCESSFUL);
            }
        }
        response.addHeader(Common.FLYWEB_HEADER, String.valueOf(mFiles.isEmpty()));
        return response;
    }

    public Response post(IHTTPSession session) {
        mSuccess = false;
        Map<String, String> files = new HashMap<>();
        Map<String, List<String>> params;

        try {
            session.parseBody(files);
            params = session.getParameters();
        } catch (ResponseException | IOException e) {
            Log.e("NanoHttpdServer", "Error reading uploaded file.");
            return generateHtmlResponse(Strings.ERROR_READING);
        }

        // TODO iterate over map for multiple files
        String tempFilePath = files.get(NANOHTTPD_KEY);
        if (tempFilePath == null || tempFilePath.isEmpty()) {
            return generateHtmlResponse(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        List<String> fileParam = params.get(NANOHTTPD_KEY);
        if (fileParam == null || fileParam.isEmpty()) {
            return generateHtmlResponse(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        // Since we are only dealing with a single upload at once for now, always get the first file name
        String originalFileName = fileParam.get(0);

        File outFile = new File(Common.DIRECTORY_PATH, originalFileName);
        write(tempFilePath, outFile);

        Response response = null;
        if (mSuccess) {
            response = generateHtmlResponse(Strings.SUCCESSFULLY_UPLOADED);
        } else {
            response = generateHtmlResponse(Strings.FAILED_UPLOAD);
        }

        return response;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getPort() {
        return port;
    }

    private Response generateHtmlResponse(String content) {
        Response response = newFixedLengthResponse(new StringBuilder("<html><body>").append(content)
                .append("</body></html>").toString());
        return response;
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
            mFiles.add(outFile);
            inStream.close();
            outStream.close();
            mSuccess = true;
        } catch (Exception e) {
            Log.e("NanoHttpdServer", "Error writing out file when uploading.");
        }
    }
}