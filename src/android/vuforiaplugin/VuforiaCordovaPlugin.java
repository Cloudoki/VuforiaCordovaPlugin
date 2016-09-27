package com.cloudoki.vuforiaplugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Vector;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import com.cloudoki.vuforiaplugin.utils.AppGLView;
import com.cloudoki.vuforiaplugin.utils.LoadingDialogHandler;
import com.cloudoki.vuforiaplugin.utils.Logger;
import com.cloudoki.vuforiaplugin.utils.Texture;
import com.cloudoki.vuforiaplugin.video.VideoPlayerHelper;
import com.cloudoki.vuforiaplugin.video.VideoPlayerHelper.MEDIA_STATE;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;


public class VuforiaCordovaPlugin extends CordovaPlugin implements VuforiaAppControl {

    private static final String TAG = "VuforiaCordovaPlugin";
    private static final int REQUEST_CAMERA_PERMISSIONS = 98;
    private static final int REQUEST_EXTERNAL_STORAGE = 99;
    private String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final String LICENSE_LOCATION = "www/license/vuforiaLicense.txt";
    private static final String ASSETS_FOLDER = "www/assets/";

    // Movie for the Targets:
    public static final int NUM_TARGETS = 2;
    public static final int FR = 1;
    public static final int NL = 0;
    private VideoPlayerHelper mVideoPlayerHelper[] = null;
    private int mSeekPosition[] = null;
    private boolean mWasPlaying[] = null;
    private String mMovieName[] = null;

    // A boolean to indicate whether we come from full screen:
    private boolean mReturningFromFullScreen = false;

    // Helpers to detect events such as double tapping:
    private GestureDetector mGestureDetector = null;
    private GestureDetector.SimpleOnGestureListener mSimpleListener = null;

    VuforiaAppSession vuforiaAppSession;

    private Activity mActivity;

    private DataSet mCurrentDataset;
    private int mCurrentDatasetSelectionIndex = 0;
    private ArrayList<String> mDatasetStrings = new ArrayList<String>();

    // OpenGL View
    private AppGLView mGlView;

    // Renderer
    private VuforiaTargetRenderer mRenderer;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures, mVideoTextures;

    private boolean mSwitchDatasetAsap = false;

    private RelativeLayout mUILayout;

    private boolean mPlayFullscreenVideo = false;

    private CordovaWebView mWebView;

    LoadingDialogHandler loadingDialogHandler;

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    boolean mIsDroidDevice = false;

    // callback to call detections
    private CallbackContext cb;

    private String detectedTarget = "";

    static String mLang = "nl";


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mActivity = cordova.getActivity();

        mWebView = webView;

        Logger.d(TAG, "initialize");

        loadingDialogHandler = new LoadingDialogHandler(mActivity);

        checkPermissions();

        String vuforiaLicense = loadLicence();

        vuforiaAppSession = new VuforiaAppSession(this, vuforiaLicense);

        startLoadingAnimation();

        mDatasetStrings.add(ASSETS_FOLDER + "OMAD.xml");
    }

    @Override
    public boolean execute(String action, JSONArray data,
                           CallbackContext callbackContext) throws JSONException {

        if (action.equals("setLang")) {
            Logger.i(TAG, "setLang called");
            String lang = data.getString(0);
            if (lang != null && !lang.isEmpty()) {
                mLang = lang;
                callbackContext.success("Language was set to: " + mLang );
            } else {
                callbackContext.error("Error: missing language.");
            }
            return true;
        }

        if (action.equals("isDetecting")) {
            Logger.i(TAG, "isDetecting called");
            cb = callbackContext;
            return true;
        }

        return false;
    }


    @Override
    public void onStart() {
        super.onStart();
        Logger.i(TAG, "onStart(): Activity starting");

        vuforiaAppSession
                .initAR(mActivity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Load any sample specific textures:
        mTextures = new Vector<Texture>();
        mVideoTextures = new Vector<Texture>();
        loadTextures();

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid");

        // Create the gesture detector that will handle the single and
        // double taps:
        mSimpleListener = new GestureDetector.SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(mActivity.getApplicationContext(), mSimpleListener);

        mVideoPlayerHelper = new VideoPlayerHelper[NUM_TARGETS];
        mSeekPosition = new int[NUM_TARGETS];
        mWasPlaying = new boolean[NUM_TARGETS];
        mMovieName = new String[NUM_TARGETS];

        // Create the video player helper that handles the playback of the movie
        // for the targets:
        for (int i = 0; i < NUM_TARGETS; i++) {
            mVideoPlayerHelper[i] = new VideoPlayerHelper();
            mVideoPlayerHelper[i].init();
            mVideoPlayerHelper[i].setActivity(mActivity);
        }

        mMovieName[NL] = ASSETS_FOLDER + "VideoPlayback/energylab25fps_nl.mp4";
        mMovieName[FR] = ASSETS_FOLDER + "VideoPlayback/energylab25fps_fr.mp4";

        // Set the double tap listener:
        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            public boolean onDoubleTap(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            public boolean onDoubleTapEvent(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            // Handle the single tap
            public boolean onSingleTapConfirmed(MotionEvent e) {
                boolean isSingleTapHandled = false;
                // Do not react if the StartupScreen is being displayed
                for (int i = 0; i < NUM_TARGETS; i++) {
                    // Verify that the tap happened inside the target
                    if (mRenderer != null && mRenderer.isTapOnScreenInsideTarget(i, e.getX(),
                            e.getY())) {
                        // Check if it is playable on texture
                        if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PAUSED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.READY)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.STOPPED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END)) {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END))
                                    mSeekPosition[i] = 0;

                                mVideoPlayerHelper[i].play(mPlayFullscreenVideo,
                                        mSeekPosition[i]);
                                mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                            } else if (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING) {
                                // If it is playing then we pause it
                                mVideoPlayerHelper[i].pause();
                            }
                        } else if (mVideoPlayerHelper[i].isPlayableFullscreen()) {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            mVideoPlayerHelper[i].play(true,
                                    VideoPlayerHelper.CURRENT_POSITION);
                        }

                        isSingleTapHandled = true;

                        // Even though multiple videos can be loaded only one
                        // can be playing at any point in time. This break
                        // prevents that, say, overlapping videos trigger
                        // simultaneously playback.
                        break;
                    }
                }

                return isSingleTapHandled;
            }
        });

        mWebView.getView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mGestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }


    // We want to load specific textures from the APK, which we will later use
    // for rendering.

    private void loadTextures() {
        mTextures.add(Texture.loadTextureFromApk(ASSETS_FOLDER + "milestone_texture.jpg",
                mActivity.getAssets()));

        mVideoTextures.add(Texture.loadTextureFromApk(
                ASSETS_FOLDER + "VideoPlayback/energylab25fps_nl.png", mActivity.getAssets()));
        mVideoTextures.add(Texture.loadTextureFromApk(
                ASSETS_FOLDER + "VideoPlayback/energylab25fps_fr.png", mActivity.getAssets()));
        mVideoTextures.add(Texture.loadTextureFromApk(ASSETS_FOLDER + "VideoPlayback/play.png",
                mActivity.getAssets()));
        mVideoTextures.add(Texture.loadTextureFromApk(ASSETS_FOLDER + "VideoPlayback/busy.png",
                mActivity.getAssets()));
        mVideoTextures.add(Texture.loadTextureFromApk(ASSETS_FOLDER + "VideoPlayback/error.png",
                mActivity.getAssets()));
    }


    // Called when the activity will start interacting with the user.
    @Override
    public void onResume(boolean multitasking) {
        Logger.d(TAG, "onResume");
        super.onResume(multitasking);

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            vuforiaAppSession.resumeAR();
        } catch (VuforiaAppException e) {
            Logger.e(TAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }

        if (mRenderer != null) {
            for (int i = 0; i < NUM_TARGETS; i++) {
                if (!mReturningFromFullScreen) {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i], false);
                } else {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i], mWasPlaying[i]);
                }
            }
        }

        mReturningFromFullScreen = false;

    }


    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config) {
        Logger.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }


    // Called when the system is about to start resuming a previous activity.
    @Override
    public void onPause(boolean multitasking) {
        Logger.d(TAG, "onPause");
        super.onPause(multitasking);

        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Store the playback state of the movies and unload them:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                mSeekPosition[i] = mVideoPlayerHelper[i].getCurrentPosition();
                mWasPlaying[i] = mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING;
            }

            // We also need to release the resources used by the helper, though
            // we don't need to destroy it:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unload();
        }

        mReturningFromFullScreen = false;

        try {
            vuforiaAppSession.pauseAR();
        } catch (VuforiaAppException e) {
            Logger.e(TAG, e.getString());
        }
    }


    // The final call you receive before your activity is destroyed.
    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
        super.onDestroy();

        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is destroyed we need to release all resources:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].deinit();
            mVideoPlayerHelper[i] = null;
        }

        try {
            vuforiaAppSession.stopAR();
        } catch (VuforiaAppException e) {
            Logger.e(TAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mVideoTextures.clear();
        mTextures = null;
        mVideoTextures = null;

        System.gc();
    }


    // Pause all movies except one
    // if the value of 'except' is -1 then
    // do a blanket pause
    private void pauseAll(int except) {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // We can make one exception to the pause all calls:
            if (i != except) {
                // Check if the video is playable on texture
                if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            }
        }
    }


    private void startLoadingAnimation() {
        String package_name = mActivity.getApplication().getPackageName();
        Resources resources = mActivity.getApplication().getResources();

        LayoutInflater inflater = LayoutInflater.from(mActivity);

        mUILayout = (RelativeLayout) inflater.inflate(resources.getIdentifier("camera_overlay", "layout", package_name),
                null, false);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(resources.getIdentifier("loading_indicator", "id", package_name));

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        mActivity.addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }


    // Initializes AR application components.
    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new AppGLView(mActivity.getApplicationContext());
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new VuforiaTargetRenderer(this, mActivity, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mRenderer.setVideoTextures(mVideoTextures);

        // The renderer comes has the OpenGL context, thus, loading to texture
        // must happen when the surface has been created. This means that we
        // can't load the movie from this thread (GUI) but instead we must
        // tell the GL thread to load it once the surface has been created.
        for (int i = 0; i < NUM_TARGETS; i++) {
            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
            mRenderer.requestLoad(i, mMovieName[i], 0, false);
        }

        for (int i = 0; i < NUM_TARGETS; i++) {
            float[] temp = {0f, 0f, 0f};
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }

        mRenderer.setObjectRotationAnimation(true);

        mGlView.setRenderer(mRenderer);
    }


    // Methods to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData() {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();

        if (mCurrentDataset == null)
            return false;

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;

        int numTrackables = mCurrentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++) {
            Trackable trackable = mCurrentDataset.getTrackable(count);

            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Logger.d(TAG, "UserData:Set the following user data "
                    + trackable.getUserData().toString());
        }

        return true;
    }


    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset != null && mCurrentDataset.isActive()) {
            if (objectTracker.getActiveDataSet().equals(mCurrentDataset)
                    && !objectTracker.deactivateDataSet(mCurrentDataset)) {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset)) {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }


    @Override
    public void onInitARDone(VuforiaAppException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            mActivity.addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            // Bring webView on top of camera
            mWebView.getView().bringToFront();

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            try {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (VuforiaAppException e) {
                Logger.e(TAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (!result)
                Logger.e(TAG, "Unable to enable continuous autofocus");

        } else {
            Logger.e(TAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                String package_name = mActivity.getApplication().getPackageName();
                Resources resources = mActivity.getApplication().getResources();

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder
                        .setMessage(errorMessage)
                        .setTitle(resources.getIdentifier("INIT_ERROR", "string", package_name))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton(resources.getIdentifier("button_OK", "string", package_name),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        mActivity.finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    @Override
    public void onVuforiaUpdate(State state) {
        if (mSwitchDatasetAsap) {
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                    .getClassType());
            if (ot == null || mCurrentDataset == null
                    || ot.getActiveDataSet() == null) {
                Logger.d(TAG, "Failed to swap datasets");
                return;
            }

            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }


    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        // Trying to initialize the image tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Logger.e(
                    TAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            Logger.i(TAG, "Tracker successfully initialized");
        }
        return result;
    }


    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.start();
            Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 2);
        } else {
            result = false;
        }

        return result;
    }


    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();
        else
            result = false;

        return result;
    }


    @Override
    public boolean doDeinitTrackers() {
        // Indicate if the trackers were deinitialized correctly

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return true;
    }

    private void checkPermissions() {
        boolean cameraPermission = ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (!cameraPermission) {
            ActivityCompat.requestPermissions(mActivity,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSIONS);
        }

        int permission = ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    mActivity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private String loadLicence() {
        BufferedReader reader = null;
        StringBuilder license = new StringBuilder();
        try {
            reader = new BufferedReader(
                    new InputStreamReader(mActivity.getAssets().open(LICENSE_LOCATION), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                license.append(line);
            }
        } catch (IOException e) {
            Logger.e(TAG, e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Logger.e(TAG, e.getMessage());
                }
            }
        }

        return license.toString();
    }

    // update cordova about target detection
    private void sendDetectionUpdate(boolean state, String targetName) {
        // plugin result
        PluginResult result;

        String jsonObj = String.format("{\"state\": %b, \"target\": \"%s\"}", state, targetName);

        if (state) {
            result = new PluginResult(PluginResult.Status.OK, jsonObj);
        } else {
            result = new PluginResult(PluginResult.Status.ERROR, jsonObj);
        }

        result.setKeepCallback(true);
        cb.sendPluginResult(result);
    }

    public void updateDetectedTarget(boolean foundTarget, String targetName) {
        if (foundTarget) {
            // if the target changed update it and notify
            if (!targetName.equalsIgnoreCase(detectedTarget)) {
                detectedTarget = targetName;
                sendDetectionUpdate(true, detectedTarget);
            }
        } else {
            // if no target is detected after a detection, update it and notify
            if (!detectedTarget.isEmpty()) {
                detectedTarget = targetName;
                sendDetectionUpdate(false, detectedTarget);
            }
        }
    }
}
