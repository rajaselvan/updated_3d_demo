/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview.demo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.cameraview.CameraView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private FrameLayout mFrameLayout;

    private CameraView mCameraView;

    private Handler mBackgroundHandler;

    private MyGLSurfaceView3 mMyGLSurfaceView;

    // The asset ID to download and display.
    private static final String ASSET_ID = "evwJECBy7fi";

    // The size we want to scale the asset to, for display. This size guarantees that no matter
    // how big or small the asset is, we will scale it to a reasonable size for viewing.
    private static final float ASSET_DISPLAY_SIZE = 5;

    // Our background thread, which does all of the heavy lifting so we don't block the main thread.
    private HandlerThread backgroundThread;

    // Handler for the background thread, to which we post background thread tasks.
    private Handler backgroundThreadHandler;

    // The AsyncFileDownloader responsible for downloading a set of data files from Poly.
    private AsyncFileDownloader fileDownloader;

    private ARDemoApp app;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraView = (CameraView) findViewById(R.id.camera);
        mFrameLayout = (FrameLayout) findViewById(R.id.fl_container);
        app = ARDemoApp.getInstance();

        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        backgroundThread = new HandlerThread("Worker");
        backgroundThread.start();
        backgroundThreadHandler = new Handler(backgroundThread.getLooper());

        // Request the asset from the Poly API.
        Log.d(TAG, "Requesting asset " + ASSET_ID);
        PolyApi.GetAsset(ASSET_ID, backgroundThreadHandler, new AsyncHttpRequest.CompletionListener() {
            @Override
            public void onHttpRequestSuccess(byte[] responseBody) {
                // Successfully fetched asset information. This does NOT include the model's geometry,
                // it's just the metadata. Let's parse it.
                parseAsset(responseBody);
            }

            @Override
            public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                // Something went wrong with the request.
                handleRequestFailure(statusCode, message, exception);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mCameraView.start();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
        if (mMyGLSurfaceView != null) {
            mMyGLSurfaceView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();
                }
                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
        }

    };

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                                                             String[] permissions, int requestCode, @StringRes int notGrantedMessage) {
            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }

    private void parseAsset(byte[] assetData) {
        Log.d(TAG, "Got asset response (" + assetData.length + " bytes). Parsing.");
        String assetBody = new String(assetData, Charset.forName("UTF-8"));
        Log.d(TAG, assetBody);
        try {
            JSONObject response = new JSONObject(assetBody);

            // Display attribution in a toast, for simplicity. In your app, you don't have to use a
            // toast to do this. You can display it where it's most appropriate for your app.
            setStatusMessageOnUiThread(response.getString("displayName") + " by " +
                    response.getString("authorName"));

            // The asset may have several formats (OBJ, GLTF, FBX, etc). We will look for the OBJ format.
            JSONArray formats = response.getJSONArray("formats");
            boolean foundObjFormat = false;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                if (format.getString("formatType").equals("OBJ")) {
                    // Found the OBJ format. The format gives us the URL of the data files that we should
                    // download (which include the OBJ file, the MTL file and the textures). We will now
                    // request those files.
                    requestDataFiles(format);
                    foundObjFormat = true;
                    break;
                }
            }
            if (!foundObjFormat) {
                // If this happens, it's because the asset doesn't have a representation in the OBJ
                // format. Since this simple sample code can only parse OBJ, we can't proceed.
                // But other formats might be available, so if your client supports multiple formats,
                // you could still try a different format instead.
                Log.e(TAG, "Could not find OBJ format in asset.");
                return;
            }
        } catch (JSONException jsonException) {
            Log.e(TAG, "JSON parsing error while processing response: " + jsonException);
            jsonException.printStackTrace();
            setStatusMessageOnUiThread("Failed to parse response.");
        }
    }

    // Requests the data files for the OBJ format.
    // NOTE: this runs on the background thread.
    private void requestDataFiles(JSONObject objFormat) throws JSONException {
        // objFormat has the list of data files for the OBJ format (OBJ file, MTL file, textures).
        // We will use a AsyncFileDownloader to download all those files.
        fileDownloader = new AsyncFileDownloader();

        // The "root file" is the OBJ.
        JSONObject rootFile = objFormat.getJSONObject("root");
        fileDownloader.add(rootFile.getString("relativePath"), rootFile.getString("url"));

        // The "resource files" are the MTL file and textures.
        JSONArray resources = objFormat.getJSONArray("resources");
        for (int i = 0; i < resources.length(); i++) {
            JSONObject resourceFile = resources.getJSONObject(i);
            String path = resourceFile.getString("relativePath");
            String url = resourceFile.getString("url");
            // For this example, we only care about OBJ and MTL files (not textures).
            if (path.toLowerCase().endsWith(".obj") || path.toLowerCase().endsWith(".mtl")) {
                fileDownloader.add(path, url);
            }
        }

        // Now start downloading the data files. When this is done, the callback will call
        // processDataFiles().
        Log.d(TAG, "Starting to download data files, # files: " + fileDownloader.getEntryCount());
        fileDownloader.start(backgroundThreadHandler, new AsyncFileDownloader.CompletionListener() {
            @Override
            public void onPolyDownloadFinished(AsyncFileDownloader downloader) {
                if (downloader.isError()) {
                    Log.e(TAG, "Failed to download data files for asset.");
                    setStatusMessageOnUiThread("Failed to download data files.");
                    return;
                }
                processDataFiles();
            }
        });
    }

    // NOTE: this runs on the background thread.
    private void processDataFiles() {
        Log.d(TAG, "All data files downloaded.");
        // At this point, all the necessary data files are downloaded in fileDownloader, so what
        // we have to do now is parse and convert those files to a format we can render.

        Model model = null;
        MtlLibrary mtlLibrary = new MtlLibrary();
        InputStream myInputStream = null;

        try {
            for (int i = 0; i < fileDownloader.getEntryCount(); i++) {
                AsyncFileDownloader.Entry entry = fileDownloader.getEntry(i);
                Log.d(TAG, "Processing: " + entry.fileName + ", length:" + entry.contents.length);
                String contents = new String(entry.contents, Charset.forName("UTF-8"));
                if (entry.fileName.toLowerCase().endsWith(".obj")) {
                    myInputStream = new ByteArrayInputStream(entry.contents);
                } else if (entry.fileName.toLowerCase().endsWith(".mtl")) {
                    mtlLibrary.parseAndAdd(contents);
                }
            }

            if (myInputStream != null) {
                model = new ObjModel(myInputStream, mtlLibrary);
                setCurrentModel(model);
            }


        } catch (MtlLibrary.MtlParseException mtlParseException) {
            Log.e(TAG, "Error parsing MTL file.");
            mtlParseException.printStackTrace();
            setStatusMessageOnUiThread("Failed to parse MTL file.");
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    // NOTE: this runs on the background thread.
    private void handleRequestFailure(int statusCode, String message, Exception exception) {
        // NOTE: because this is a simple sample, we don't have any real error handling logic
        // other than just printing the error. In an actual app, this is where you would take
        // appropriate action according to your app's use case. You could, for example, surface
        // the error to the user or retry the request later.
        Log.e(TAG, "Request failed. Status code " + statusCode + ", message: " + message +
                ((exception != null) ? ", exception: " + exception : ""));
        if (exception != null) exception.printStackTrace();
        setStatusMessageOnUiThread("Request failed. See logs.");
    }

    // NOTE: this runs on the background thread.
    private void setStatusMessageOnUiThread(final String statusMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    private void setCurrentModel(@NonNull final Model model) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createNewModelView(model);
            }
        });
    }

    private void createNewModelView(@Nullable Model model) {
        if (mMyGLSurfaceView != null) {
            mFrameLayout.removeView(mMyGLSurfaceView);
        }
        ARDemoApp.getInstance().setCurrentModel(model);
        mMyGLSurfaceView = new MyGLSurfaceView3(this, model);
        mFrameLayout.addView(mMyGLSurfaceView);
    }


    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
        if (mMyGLSurfaceView != null) {
            mMyGLSurfaceView.onPause();
        }
    }
}
