package ch.ti8m.phonegap.plugins;

import java.io.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.cordova.BuildConfig;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.pm.PackageManager;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;

import android.support.v4.content.FileProvider;

public class DocumentHandler extends CordovaPlugin {

    public static final String HANDLE_DOCUMENT_ACTION = "HandleDocumentWithURL";
    public static final int ERROR_NO_HANDLER_FOR_DATA_TYPE = 53;
    public static final int ERROR_GENERIC_THROWABLE = 3;
    public static final int ERROR_FILE_NOT_FOUND = 2;
    public static final int ERROR_UNKNOWN_ERROR = 1;
	private static String FILE_PROVIDER_PACKAGE_ID;

    @Override
    public boolean execute(String action, JSONArray args,
            final CallbackContext callbackContext) throws JSONException {
        if (HANDLE_DOCUMENT_ACTION.equals(action)) {

            // parse arguments
            final JSONObject arg_object = args.getJSONObject(0);
            final String url = arg_object.getString("url");
            final String fileName = arg_object.getString("fileName");
            final String type = arg_object.getString("type");
			FILE_PROVIDER_PACKAGE_ID = cordova.getActivity().getPackageName() + ".fileprovider";
            System.out.println("Found: " + url);

            // start async download task
            new FileDownloaderAsyncTask(callbackContext, url, fileName, type).execute();

            return true;
        }
        return false;
    }

    // used for all downloaded files, so we can find and delete them again.
    private final static String FILE_PREFIX = "DH_";

    /**
     * downloads a file from the given url to external storage.
     *
     * @param url
     * @return
     */
    private File downloadFile(String fileName, String url, CallbackContext callbackContext) {

        File f = null;

        try {

			// get an instance of a cookie manager since it has access to our
            // auth cookie
            CookieManager cookieManager = CookieManager.getInstance();

            // get the cookie string for the site.
            String auth = null;
            if (cookieManager.getCookie(url) != null) {
                auth = cookieManager.getCookie(url).toString();
            }

            URL url2 = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) url2.openConnection();
            if (auth != null) {
                conn.setRequestProperty("Cookie", auth);
            }

            InputStream reader = conn.getInputStream();

            Context context = cordova.getActivity().getApplicationContext();
            File directory = context.getExternalFilesDir(null);
            //System.out.println("directory: " + Uri.fromFile(directory).toString());



            if (fileName.isEmpty()) {
                //String extension = MimeTypeMap.getFileExtensionFromUrl(url);
                String extension = getExtensionFromUrl(url);
                if (extension != null) {
                    System.out.println("no extension: default to pdf");
                    extension = "pdf";
                }
                System.out.println("creating file with extension: " + extension);

                f = File.createTempFile(FILE_PREFIX, "." + extension, directory);
            } else { 
                f = new File(directory, fileName);
                f.createNewFile();
                // TODO: Change behaviour if the file already exists?
            }

            // make sure the receiving app can read this file
            f.setReadable(true, false);
            FileOutputStream outStream = new FileOutputStream(f);

            byte[] buffer = new byte[1024];
            int readBytes = reader.read(buffer);
            while (readBytes > 0) {
                outStream.write(buffer, 0, readBytes);
                readBytes = reader.read(buffer);
            }
            reader.close();
            outStream.close();
            return f;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            callbackContext.error(buildErrorObject(ERROR_FILE_NOT_FOUND, e, url, f));
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            callbackContext.error(buildErrorObject(ERROR_UNKNOWN_ERROR, e, url, f));
            return null;
        } catch (Throwable e) {
            e.printStackTrace();
            callbackContext.error(buildErrorObject(ERROR_GENERIC_THROWABLE, e, url, f));
            return null;
        }
    }

    private JSONObject buildErrorObject(int error_code, java.lang.Throwable e, String url, File f) {

        JSONObject error = new JSONObject();

        Boolean includeTrace = false;

        if(url.startsWith(("https://dev"))) {
            includeTrace = true;
        }

        try {
            error.put("code", error_code);
            error.put("error", e.toString());

            if(f != null) {
                error.put("file", f.getAbsolutePath());
            }

            if(includeTrace) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                error.put("trace", sw.toString());
            }
        } catch (org.json.JSONException ex) {
            System.out.println("Unexpected JSONException: " + ex.toString());
        }

        return error;
    }

    private static String getExtensionFromUrl(String url) { 

        /*
         * MimeTypeMap.getFileExtensionFromUrl can return an empty string when there are
         * spaces in the 'url'
         * see comments here: https://stackoverflow.com/questions/14320527/android-should-i-use-mimetypemap-getfileextensionfromurl-bugs/14321470
         */
        //String extension = MimeTypeMap.getFileExtensionFromUrl(url);

        String extension = null;

        int lastDot = url.lastIndexOf('.');

        if(lastDot != -1) {
            extension = url.substring(lastDot+1);
        }

        return extension;
    }


    /**
     * Returns the MIME Type of the file by looking at file name extension in
     * the URL.
     *
     * @param url
     * @return
     */
    private static String getMimeType(String url) {
        String mimeType = null;

        String extension = getExtensionFromUrl(url);

        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            mimeType = mime.getMimeTypeFromExtension(extension);
        }

        if (mimeType == null) {
            mimeType = "application/pdf";
            System.out.println("Mime Type (default): " + mimeType);
        } else { 
            System.out.println("Mime Type: " + mimeType);
        }

        return mimeType;
    }

    private class FileDownloaderAsyncTask extends AsyncTask<Void, Void, File> {

        private final CallbackContext callbackContext;
        private final String url;
        private final String fileName;
        private final String type;

        public FileDownloaderAsyncTask(CallbackContext callbackContext,
                String url, String fileName, String type) {
            super();
            this.callbackContext = callbackContext;
            this.url = url;
            this.fileName = fileName;
            this.type = type;
        }

        @Override
        protected File doInBackground(Void... arg0) {
            if (!url.startsWith("file://")) {
                return downloadFile(this.fileName, url, callbackContext);
            } else {
                File file = new File(url.replace("file://", ""));
                return file;
            }
        }

        @Override
        protected void onPostExecute(File result) {
            if (result == null) {
                // case has already been handled
                return;
            }

            Context context = cordova.getActivity().getApplicationContext();

            // get mime type of file data
            String mimeType = type;
            if (mimeType.isEmpty()) mimeType = getMimeType(url);
            if (mimeType == null) {
                callbackContext.error(ERROR_UNKNOWN_ERROR);
                return;
            }

            // start an intent with the file
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
				Uri contentUri = FileProvider.getUriForFile(context, FILE_PROVIDER_PACKAGE_ID, result);
                intent.setDataAndType(contentUri, mimeType);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				PackageManager pm = context.getPackageManager();
                context.startActivity(intent);

                callbackContext.success(fileName); // Thread-safe.
            } catch (ActivityNotFoundException e) {
				// happens when we start intent without something that can
                // handle it
                e.printStackTrace();
                callbackContext.error(ERROR_NO_HANDLER_FOR_DATA_TYPE);
            }

        }

    }

}
