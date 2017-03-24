package ca.vijayan.flyweb.embedded_server;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Irene Chen on 3/12/2017.
 */

public class EmbeddedServer extends NanoHTTPD {
    public  static final int DEFAULT_PORT = 8080; // TODO make port allocation dynamic
    private final String NANOHTTPD_KEY = "file";
    private final String MIME_CSS = "text/css";
    private final int MAX_FILE_SIZE_IN_BYTES = 5000000;
    private int port;
    private Activity mActivity;
    private boolean mSuccess;
    private List<File> mFiles;
    private String mServiceName;

    public EmbeddedServer(Activity activity) {
        super(DEFAULT_PORT);
        mServiceName = Strings.FILE_SHARED_BY + Build.MODEL;
        port = DEFAULT_PORT;
        mActivity = activity;
        mFiles = new ArrayList<>();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.endsWith(".css")) {
            try {
                InputStream is = mActivity.getApplicationContext().getAssets().open(uri.substring(1));
                return newChunkedResponse(Response.Status.OK, MIME_CSS, is);
            } catch (IOException e) {
                Log.e("EmbeddedServer", "Failed to get style sheets.");
            }
        }
        return Method.POST.equals(session.getMethod()) ? post(session) : get();
    }

    public Response get() {
        Response response = null;
        if (mFiles.isEmpty()) {
            response = generateGenericHtmlResponse("<div class =\"jumbotron\">"
                    + "<h1 class=\"display-3\">Upload File</h3>"
                    + "<form name='upload' method='post' enctype='multipart/form-data' action='" + Common.LOCALHOST
                    + port + Common.UPLOAD_ENDPOINT + "' />"
                    + "<input type='file' name='file' /><br />"
                    + "<input type='submit' name='submit' value='" + Strings.UPLOAD + "' />"
                    + "</div>");
        } else {
            try {
                for (File file : mFiles) { // there should only be one element in the map right now; return on first loop
                    FileInputStream fis = new FileInputStream(file);
                    response = newChunkedResponse(Response.Status.OK, Common.getMimeType(Uri.fromFile(file).toString()),
                            fis);
                    response.addHeader(Common.HEADER_FILENAME_KEY, file.getName());
                }
            } catch (Exception e) {
                Log.e("EmbeddedServer", "Error download files.");
                response = generateHtmlResponseWithLeadClass(Strings.DOWNLOAD_UNSUCCESSFUL);
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
            Log.e("EmbeddedServer", "Error reading uploaded file.");
            return generateHtmlResponseWithLeadClass(Strings.ERROR_READING);
        }

        // TODO iterate over map for multiple files
        String tempFilePath = files.get(NANOHTTPD_KEY);
        if (tempFilePath == null || tempFilePath.isEmpty()) {
            return generateHtmlResponseWithLeadClass(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        List<String> fileParam = params.get(NANOHTTPD_KEY);
        if (fileParam == null || fileParam.isEmpty()) {
            return generateHtmlResponseWithLeadClass(Strings.PLEASE_UPLOAD_VALID_FILE);
        }

        // Since we are only dealing with a single upload at once for now, always get the first file name
        String originalFileName = fileParam.get(0);

        File outFile = new File(Common.DIRECTORY_PATH, originalFileName);
        write(tempFilePath, outFile);

        Response response = null;
        if (mSuccess) {
            response = generateHtmlResponseWithLeadClass(Strings.SUCCESSFULLY_UPLOADED);
        } else {
            response = generateHtmlResponseWithLeadClass(Strings.FAILED_UPLOAD);
        }

        return response;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getPort() {
        return port;
    }

    private Response generateGenericHtmlResponse(String content) {
        Response response = newFixedLengthResponse(new StringBuilder(generateHtmlResponseHeader())
                .append(content)
                .append(generateHtmlResponseFooter())
                .toString());
        return response;
    }

    private Response generateHtmlResponseWithLeadClass(String content) {
        Response response = newFixedLengthResponse(new StringBuilder(generateHtmlResponseHeader())
                .append("<p class=\"lead\">")
                .append(content)
                .append("</p>")
                .append(generateHtmlResponseFooter())
                .toString());
        return response;
    }

    private String generateHtmlResponseHeader() {
        return "<html><head><link rel=\"stylesheet\" type=" +
                "\"text/css\" href=\"bootstrap.min.css\"></head><body><div class=\"container\"><br />";
    }

    private String generateHtmlResponseFooter() {
        return "</div></body></html>";
    }

    private void write(String srcPath, File outFile) {
        int totalBytesRead = 0;
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
            Log.e("EmbeddedServer", "Error writing out file when uploading.");
        }
    }
}