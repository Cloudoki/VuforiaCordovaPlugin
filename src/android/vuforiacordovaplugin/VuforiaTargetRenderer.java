/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.cloudoki.vuforiacordovaplugin;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.cloudoki.vuforiacordovaplugin.utils.CubeShaders;
import com.cloudoki.vuforiacordovaplugin.utils.LoadingDialogHandler;
import com.cloudoki.vuforiacordovaplugin.utils.Logger;
import com.cloudoki.vuforiacordovaplugin.utils.Math;
import com.cloudoki.vuforiacordovaplugin.utils.ObjectParser;
import com.cloudoki.vuforiacordovaplugin.utils.Texture;
import com.cloudoki.vuforiacordovaplugin.utils.Utils;
import com.cloudoki.vuforiacordovaplugin.video.KeyFrameShaders;
import com.cloudoki.vuforiacordovaplugin.video.VideoPlaybackShaders;
import com.cloudoki.vuforiacordovaplugin.video.VideoPlayerHelper;
import com.vuforia.Device;
import com.vuforia.ImageTarget;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.Vuforia;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class VuforiaTargetRenderer implements GLSurfaceView.Renderer, AppRendererControl {

    private static final String LOGTAG = "VuforiaTargetRenderer";

    private static final String ASSETS_FOLDER = "www/assets/";

    private static final float OBJECT_SCALE_FLOAT = 3.0f * 10;

    private VuforiaAppSession mVuforiaAppSession;
    private VuforiaCordovaPlugin mVuforiaCordovaPlugin;
    private Activity mActivity;

    private AppRenderer mAppRenderer;

    private ObjectParser mModel;

    private Renderer mRenderer;

    private Vector<Texture> mTextures, mVideoTextures;

    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;

    private boolean mIsActive = false;
    private boolean mModelsLoaded = false;

    // ------------ Video Playback Rendering Specific
    private int videoPlaybackShaderID = 0;
    private int videoPlaybackVertexHandle = 0;
    private int videoPlaybackTexCoordHandle = 0;
    private int videoPlaybackMVPMatrixHandle = 0;
    private int videoPlaybackTexSamplerOESHandle = 0;

    private static int NUM_TARGETS = VuforiaCordovaPlugin.NUM_TARGETS;

    // Video Playback Textures for the targets
    public int[] videoPlaybackTextureID = new int[NUM_TARGETS];

    // Keyframe and icon rendering specific
    private int keyframeShaderID = 0;
    private int keyframeVertexHandle = 0;
    private int keyframeTexCoordHandle = 0;
    private int keyframeMVPMatrixHandle = 0;
    private int keyframeTexSampler2DHandle = 0;

    // We cannot use the default texture coordinates of the quad since these
    // will change depending on the video itself
    private float videoQuadTextureCoords[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};

    // This variable will hold the transformed coordinates (changes every frame)
    private float videoQuadTextureCoordsTransformedFR[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};

    private float videoQuadTextureCoordsTransformedNL[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};

    // Trackable dimensions
    public Vec3F[] targetPositiveDimensions = new Vec3F[NUM_TARGETS];

    static int NUM_QUAD_VERTEX = 4;
    static int NUM_QUAD_INDEX = 6;

    double quadVerticesArray[] = {-1.0f, -1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f};

    double quadTexCoordsArray[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};

    double quadNormalsArray[] = {0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,};

    short quadIndicesArray[] = {0, 1, 2, 2, 3, 0};

    Buffer quadVertices, quadTexCoords, quadIndices, quadNormals;

    private float[][] mTexCoordTransformationMatrix = null;
    private VideoPlayerHelper mVideoPlayerHelper[] = null;
    private String mMovieName[] = null;
    private VideoPlayerHelper.MEDIA_TYPE mCanRequestType[] = null;
    private int mSeekPosition[] = null;
    private boolean mShouldPlayImmediately[] = null;
    private long mLostTrackingSince[] = null;
    private boolean mLoadRequested[] = null;

    // Needed to calculate whether a screen tap is inside the target
    Matrix44F modelViewMatrix[] = new Matrix44F[NUM_TARGETS];

    boolean isTracking[] = new boolean[NUM_TARGETS];
    VideoPlayerHelper.MEDIA_STATE currentStatus[] = new VideoPlayerHelper.MEDIA_STATE[NUM_TARGETS];

    // These hold the aspect ratio of both the video and the
    // keyframe
    float videoQuadAspectRatio[] = new float[NUM_TARGETS];
    float keyframeQuadAspectRatio[] = new float[NUM_TARGETS];

    private boolean mRotate = false;
    private float mAngle = 0;

    private boolean mPaused = false;


    public VuforiaTargetRenderer(VuforiaCordovaPlugin vcp, Activity activity, VuforiaAppSession session) {
        mVuforiaCordovaPlugin = vcp;
        mVuforiaAppSession = session;
        mActivity = activity;

        // AppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mAppRenderer = new AppRenderer(this, Device.MODE.MODE_AR, false);

        // Create an array of the size of the number of targets we have
        mVideoPlayerHelper = new VideoPlayerHelper[NUM_TARGETS];
        mMovieName = new String[NUM_TARGETS];
        mCanRequestType = new VideoPlayerHelper.MEDIA_TYPE[NUM_TARGETS];
        mSeekPosition = new int[NUM_TARGETS];
        mShouldPlayImmediately = new boolean[NUM_TARGETS];
        mLostTrackingSince = new long[NUM_TARGETS];
        mLoadRequested = new boolean[NUM_TARGETS];
        mTexCoordTransformationMatrix = new float[NUM_TARGETS][16];

        // Initialize the arrays to default values
        for (int i = 0; i < NUM_TARGETS; i++) {
            mVideoPlayerHelper[i] = null;
            mMovieName[i] = "";
            mCanRequestType[i] = VideoPlayerHelper.MEDIA_TYPE.ON_TEXTURE_FULLSCREEN;
            mSeekPosition[i] = 0;
            mShouldPlayImmediately[i] = false;
            mLostTrackingSince[i] = -1;
            mLoadRequested[i] = false;
        }

        for (int i = 0; i < NUM_TARGETS; i++)
            targetPositiveDimensions[i] = new Vec3F();

        for (int i = 0; i < NUM_TARGETS; i++)
            modelViewMatrix[i] = new Matrix44F();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Logger.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        mVuforiaAppSession.onSurfaceCreated();

        mAppRenderer.onSurfaceCreated();

        for (int i = 0; i < NUM_TARGETS; i++) {
            if (mVideoPlayerHelper[i] != null) {
                // The VideoPlayerHelper needs to setup a surface texture given
                // the texture id
                // Here we inform the video player that we would like to play
                // the movie
                // both on texture and on full screen
                // Notice that this does not mean that the platform will be able
                // to do what we request
                // After the file has been loaded one must always check with
                // isPlayableOnTexture() whether
                // this can be played embedded in the AR scene
                if (!mVideoPlayerHelper[i].setupSurfaceTexture(videoPlaybackTextureID[i]))
                    mCanRequestType[i] = VideoPlayerHelper.MEDIA_TYPE.FULLSCREEN;
                else
                    mCanRequestType[i] = VideoPlayerHelper.MEDIA_TYPE.ON_TEXTURE_FULLSCREEN;

                // And now check if a load has been requested with the
                // parameters passed from the main activity
                if (mLoadRequested[i]) {
                    mVideoPlayerHelper[i].load(mMovieName[i],
                            mCanRequestType[i], mShouldPlayImmediately[i],
                            mSeekPosition[i]);
                    mLoadRequested[i] = false;
                }
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Logger.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        mVuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        mAppRenderer.onConfigurationChanged();

        init3DRendering();

        for (int i = 0; i < NUM_TARGETS; i++) {
            if (mLoadRequested[i] && mVideoPlayerHelper[i] != null) {
                mVideoPlayerHelper[i].load(mMovieName[i], mCanRequestType[i],
                        mShouldPlayImmediately[i], mSeekPosition[i]);
                mLoadRequested[i] = false;
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mIsActive)
            return;

        //--READ data
        SharedPreferences preferences = mActivity.getApplicationContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        Boolean playOnDetection = preferences.getBoolean("playOnDetection", true);

        for (int i = 0; i < NUM_TARGETS; i++) {
            if (mVideoPlayerHelper[i] != null) {
                if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                    if(isTracking(i) && playOnDetection && !mPaused) {
                        // auto-play video if target is being detected
                        if ((mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.PAUSED)
                                || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.READY)
                                || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.STOPPED)
                                || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END)) {
                            // Pause all other media
                            for (int j = 0; j < NUM_TARGETS; j++) {
                                // We can make one exception to the pause all calls:
                                if (j != i) {
                                    // Check if the video is playable on texture
                                    if (mVideoPlayerHelper[j].isPlayableOnTexture()) {
                                        // If it is playing then we pause it
                                        mVideoPlayerHelper[j].pause();
                                    }
                                }
                            }

                            // If it has reached the end then rewind
                            if ((mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END))
                                mSeekPosition[i] = 0;

                            mVideoPlayerHelper[i].play(false, mSeekPosition[i]);
                            mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                        }
                    }

                    // First we need to update the video data. This is a built
                    // in Android call
                    // Here, the decoded data is uploaded to the OES texture
                    // We only need to do this if the movie is playing
                    if (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING) {
                        mVideoPlayerHelper[i].updateVideoData();
                    }

                    // According to the Android API
                    // (http://developer.android.com/reference/android/graphics/SurfaceTexture.html)
                    // transforming the texture coordinates needs to happen
                    // every frame.
                    mVideoPlayerHelper[i]
                            .getSurfaceTextureTransformMatrix(mTexCoordTransformationMatrix[i]);
                    setVideoDimensions(i,
                            mVideoPlayerHelper[i].getVideoWidth(),
                            mVideoPlayerHelper[i].getVideoHeight(),
                            mTexCoordTransformationMatrix[i]);
                }

                setStatus(i, mVideoPlayerHelper[i].getStatus().getNumericType());
            }
        }
        // If you would like the video to start playing as soon as it starts
        // tracking
        // and pause as soon as tracking is lost you can do that here by
        // commenting
        // the for-loop above and instead checking whether the isTracking()
        // value has
        // changed since the last frame. Notice that you need to be careful not
        // to
        // trigger automatic playback for fullscreen since that will be
        // inconvenient
        // for your users.

        // clear video tracking values
        float temp[] = {0.0f, 0.0f, 0.0f};
        for (int i = 0; i < VuforiaCordovaPlugin.NUM_TARGETS; i++) {
            isTracking[i] = false;
            targetPositiveDimensions[i].setData(temp);
        }

        boolean lostTracking = false;

        // Call our function to render content
        String target = renderFrame();
        if(!target.isEmpty() && target.equalsIgnoreCase("tombstone")) {
            if(mModelsLoaded)
                mAppRenderer.render();
        } else if(!target.isEmpty() && target.equalsIgnoreCase("tia")) {
            // Call our function to render content
            renderVideo(temp);
        }

        if(target.isEmpty() || !target.equalsIgnoreCase("tia")) {
            lostTracking = true;
        }

        for (int i = 0; i < NUM_TARGETS; i++) {
            // Ask whether the target is currently being tracked and if so react
            // to it
            if (isTracking(i)) {
                // If it is tracking reset the timestamp for lost tracking
                mLostTrackingSince[i] = -1;
            } else {
                // reset for autoplay if tracking is lost
                if(mPaused && lostTracking) {
                    mPaused = false;
                }
                // If it isn't tracking
                // check whether it just lost it or if it's been a while
                if (mLostTrackingSince[i] < 0)
                    mLostTrackingSince[i] = SystemClock.uptimeMillis();
                else {
                    // If it's been more than 2 seconds then pause the player
                    if ((SystemClock.uptimeMillis() - mLostTrackingSince[i]) > 2000) {
                        if (mVideoPlayerHelper[i] != null) {
                            mVideoPlayerHelper[i].pause();
                        }
                    }
                }
            }
        }
    }

    // Function for initializing the renderer.
    private void initRendering() {
        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        // Now generate the OpenGL texture objects and add settings
        for (Texture t : mVideoTextures) {
            // Here we create the textures for the keyframe
            // and for all the icons
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        // Now we create the texture for the video data from the movie
        // IMPORTANT:
        // Notice that the textures are not typical GL_TEXTURE_2D textures
        // but instead are GL_TEXTURE_EXTERNAL_OES extension textures
        // This is required by the Android SurfaceTexture
        for (int i = 0; i < NUM_TARGETS; i++) {
            GLES20.glGenTextures(1, videoPlaybackTextureID, i);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    videoPlaybackTextureID[i]);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        // The first shader is the one that will display the video data of the
        // movie
        // (it is aware of the GL_TEXTURE_EXTERNAL_OES extension)
        videoPlaybackShaderID = Utils.createProgramFromShaderSrc(
                VideoPlaybackShaders.VIDEO_PLAYBACK_VERTEX_SHADER,
                VideoPlaybackShaders.VIDEO_PLAYBACK_FRAGMENT_SHADER);
        videoPlaybackVertexHandle = GLES20.glGetAttribLocation(
                videoPlaybackShaderID, "vertexPosition");
        videoPlaybackTexCoordHandle = GLES20.glGetAttribLocation(
                videoPlaybackShaderID, "vertexTexCoord");
        videoPlaybackMVPMatrixHandle = GLES20.glGetUniformLocation(
                videoPlaybackShaderID, "modelViewProjectionMatrix");
        videoPlaybackTexSamplerOESHandle = GLES20.glGetUniformLocation(
                videoPlaybackShaderID, "texSamplerOES");

        // This is a simpler shader with regular 2D textures
        keyframeShaderID = Utils.createProgramFromShaderSrc(
                KeyFrameShaders.KEY_FRAME_VERTEX_SHADER,
                KeyFrameShaders.KEY_FRAME_FRAGMENT_SHADER);
        keyframeVertexHandle = GLES20.glGetAttribLocation(keyframeShaderID,
                "vertexPosition");
        keyframeTexCoordHandle = GLES20.glGetAttribLocation(keyframeShaderID,
                "vertexTexCoord");
        keyframeMVPMatrixHandle = GLES20.glGetUniformLocation(keyframeShaderID,
                "modelViewProjectionMatrix");
        keyframeTexSampler2DHandle = GLES20.glGetUniformLocation(
                keyframeShaderID, "texSampler2D");

        keyframeQuadAspectRatio[VuforiaCordovaPlugin.NL] = (float) mVideoTextures.get(0).mHeight
                / (float) mVideoTextures.get(0).mWidth;
        keyframeQuadAspectRatio[VuforiaCordovaPlugin.FR] = (float) mVideoTextures.get(0).mHeight
                / (float) mVideoTextures.get(0).mWidth;

        quadVertices = fillBuffer(quadVerticesArray);
        quadTexCoords = fillBuffer(quadTexCoordsArray);
        quadIndices = fillBuffer(quadIndicesArray);
        quadNormals = fillBuffer(quadNormalsArray);
    }

    // Function for initializing the renderer.
    private void init3DRendering() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures) {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = Utils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D");

        if(!mModelsLoaded) {
            try {
                mModel = new ObjectParser(ASSETS_FOLDER + "milestone.obj", mActivity.getAssets());
                mModelsLoaded = mModel.didLoad();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(mActivity.getApplicationContext(), "Failed to load all models", Toast.LENGTH_SHORT).show();
                mActivity.finish();
            }

            // Hide the Loading Dialog
            mVuforiaCordovaPlugin.loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }

    private Buffer fillBuffer(double[] array) {
        // Convert to floats because OpenGL doesnt work on doubles, and manually
        // casting each input value would take too much time.
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length); // each
        // float
        // takes 4
        // bytes
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (double d : array)
            bb.putFloat((float) d);
        bb.rewind();

        return bb;

    }


    private Buffer fillBuffer(short[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(2 * array.length); // each
        // short
        // takes 2
        // bytes
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : array)
            bb.putShort(s);
        bb.rewind();

        return bb;

    }


    private Buffer fillBuffer(float[] array) {
        // Convert to floats because OpenGL doesnt work on doubles, and manually
        // casting each input value would take too much time.
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length); // each
        // float
        // takes 4
        // bytes
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (float d : array)
            bb.putFloat(d);
        bb.rewind();

        return bb;

    }

    private String renderFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        // did we find any trackables this frame?
        boolean foundIt = false;
        String obj_name = "";
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();

            foundIt = true;
            obj_name = trackable.getName();
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        mRenderer.end();

        mVuforiaCordovaPlugin.updateDetectedTarget(foundIt, obj_name);

        return obj_name;
    }

    public void setActive(boolean mIsActive) {
        this.mIsActive = mIsActive;
    }

    public void setTextures(Vector<Texture> textures) {
        mTextures = textures;
    }

    public void setVideoTextures(Vector<Texture> textures) {
        mVideoTextures = textures;
    }

    @Override
    public void renderFrame(State state, float[] projectionMatrix) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();

            Matrix44F modelViewMatrix_Vuforia = Tool
                    .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            if(trackable.getName().equalsIgnoreCase("tombstone")) {
                int textureIndex = 0;

                // deal with the modelview and projection matrices
                float[] modelViewProjection = new float[16];

                Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
                        OBJECT_SCALE_FLOAT);

                // set tombstone orientation
                float rotationAngle = 90.0f, activeAxis = 1.0f, inactiveAxis = 0.0f;
                Matrix.rotateM(modelViewMatrix, 0, -rotationAngle, -activeAxis, inactiveAxis, inactiveAxis);

                if(mRotate) {
                    mAngle -= 1.0f;
                    Matrix.rotateM(modelViewMatrix, 0, mAngle, inactiveAxis, activeAxis, inactiveAxis);
                }

                Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                        OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
                Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);

                // activate the shader program and bind the vertex/normal/tex coords
                GLES20.glUseProgram(shaderProgramID);

                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mModel.getVertices());
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mModel.getTexCoords());

                GLES20.glEnableVertexAttribArray(vertexHandle);
                GLES20.glEnableVertexAttribArray(textureCoordHandle);

                // activate texture 0, bind it, and pass to shader
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures.get(textureIndex).mTextureID[0]);
                GLES20.glUniform1i(texSampler2DHandle, 0);

                // pass the model view matrix to the shader
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0);

                // finally draw the teapot
//                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mModel.getNumObjectVertex());
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                        mModel.getNumObjectIndex(), GLES20.GL_UNSIGNED_INT,
                        mModel.getIndices());

                // disable the enabled arrays
                GLES20.glDisableVertexAttribArray(vertexHandle);
                GLES20.glDisableVertexAttribArray(textureCoordHandle);
            }

            Utils.checkGLError("Render Frame");

        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    private void renderVideo(float[] temp) {
        // Clear color and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Get the state from Vuforia and mark the beginning of a rendering
        // section
        State state = Renderer.getInstance().begin();

        // Explicitly render the Video Background
        Renderer.getInstance().drawVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Set the viewport
        int[] viewport = mVuforiaAppSession.getViewport();
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        //--READ data
        SharedPreferences preferences = mActivity.getApplicationContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        String lang = preferences.getString("lang", "nl");

        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            // Get the trackable:
            TrackableResult trackableResult = state.getTrackableResult(tIdx);

            ImageTarget imageTarget = (ImageTarget) trackableResult
                    .getTrackable();

            int currentTarget;

            // We store the modelview matrix to be used later by the tap
            // calculation
            if (imageTarget.getName().compareTo("tia") == 0) {
                currentTarget = lang.equalsIgnoreCase("nl") ?
                        VuforiaCordovaPlugin.NL : VuforiaCordovaPlugin.FR;

                modelViewMatrix[currentTarget] = Tool
                        .convertPose2GLMatrix(trackableResult.getPose());

                isTracking[currentTarget] = true;

                targetPositiveDimensions[currentTarget] = imageTarget.getSize();

                // The pose delivers the center of the target, thus the dimensions
                // go from -width/2 to width/2, same for height
                temp[0] = targetPositiveDimensions[currentTarget].getData()[0] / 2.0f;
                temp[1] = targetPositiveDimensions[currentTarget].getData()[1] / 2.0f;
                targetPositiveDimensions[currentTarget].setData(temp);

                // If the movie is ready to start playing or it has reached the end
                // of playback we render the keyframe
                if ((currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.READY)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.REACHED_END)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.NOT_READY)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.ERROR)) {
                    float[] modelViewMatrixKeyframe = Tool.convertPose2GLMatrix(
                            trackableResult.getPose()).getData();
                    float[] modelViewProjectionKeyframe = new float[16];
                    // Matrix.translateM(modelViewMatrixKeyframe, 0, 0.0f, 0.0f,
                    // targetPositiveDimensions[currentTarget].getData()[0]);

                    // Here we use the aspect ratio of the keyframe since it
                    // is likely that it is not a perfect square

                    float ratio = 1.0f;
                    if (mVideoTextures.get(currentTarget).mSuccess)
                        ratio = keyframeQuadAspectRatio[currentTarget];
                    else
                        ratio = targetPositiveDimensions[currentTarget].getData()[1]
                                / targetPositiveDimensions[currentTarget].getData()[0];

                    Matrix.scaleM(modelViewMatrixKeyframe, 0,
                            targetPositiveDimensions[currentTarget].getData()[0],
                            targetPositiveDimensions[currentTarget].getData()[0]
                                    * ratio,
                            targetPositiveDimensions[currentTarget].getData()[0]);
                    Matrix.multiplyMM(modelViewProjectionKeyframe, 0,
                            mVuforiaAppSession.getProjectionMatrix().getData(), 0,
                            modelViewMatrixKeyframe, 0);

                    GLES20.glUseProgram(keyframeShaderID);

                    // Prepare for rendering the keyframe
                    GLES20.glVertexAttribPointer(keyframeVertexHandle, 3,
                            GLES20.GL_FLOAT, false, 0, quadVertices);
                    GLES20.glVertexAttribPointer(keyframeTexCoordHandle, 2,
                            GLES20.GL_FLOAT, false, 0, quadTexCoords);

                    GLES20.glEnableVertexAttribArray(keyframeVertexHandle);
                    GLES20.glEnableVertexAttribArray(keyframeTexCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    // The first loaded texture from the assets folder is the
                    // keyframe
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                            mVideoTextures.get(currentTarget).mTextureID[0]);
                    GLES20.glUniformMatrix4fv(keyframeMVPMatrixHandle, 1, false,
                            modelViewProjectionKeyframe, 0);
                    GLES20.glUniform1i(keyframeTexSampler2DHandle, 0);

                    // Render
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX,
                            GLES20.GL_UNSIGNED_SHORT, quadIndices);

                    GLES20.glDisableVertexAttribArray(keyframeVertexHandle);
                    GLES20.glDisableVertexAttribArray(keyframeTexCoordHandle);

                    GLES20.glUseProgram(0);
                } else
                // In any other case, such as playing or paused, we render
                // the actual contents
                {
                    float[] modelViewMatrixVideo = Tool.convertPose2GLMatrix(
                            trackableResult.getPose()).getData();
                    float[] modelViewProjectionVideo = new float[16];
                    // Matrix.translateM(modelViewMatrixVideo, 0, 0.0f, 0.0f,
                    // targetPositiveDimensions[currentTarget].getData()[0]);

                    // Here we use the aspect ratio of the video frame
                    Matrix.scaleM(modelViewMatrixVideo, 0,
                            targetPositiveDimensions[currentTarget].getData()[0],
                            targetPositiveDimensions[currentTarget].getData()[0]
                                    * videoQuadAspectRatio[currentTarget],
                            targetPositiveDimensions[currentTarget].getData()[0]);
                    Matrix.multiplyMM(modelViewProjectionVideo, 0,
                            mVuforiaAppSession.getProjectionMatrix().getData(), 0,
                            modelViewMatrixVideo, 0);

                    GLES20.glUseProgram(videoPlaybackShaderID);

                    // Prepare for rendering the keyframe
                    GLES20.glVertexAttribPointer(videoPlaybackVertexHandle, 3,
                            GLES20.GL_FLOAT, false, 0, quadVertices);

                    if (currentTarget == VuforiaCordovaPlugin.NL)
                        GLES20.glVertexAttribPointer(videoPlaybackTexCoordHandle,
                                2, GLES20.GL_FLOAT, false, 0,
                                fillBuffer(videoQuadTextureCoordsTransformedNL));
                    else
                        GLES20.glVertexAttribPointer(videoPlaybackTexCoordHandle,
                                2, GLES20.GL_FLOAT, false, 0,
                                fillBuffer(videoQuadTextureCoordsTransformedFR));

                    GLES20.glEnableVertexAttribArray(videoPlaybackVertexHandle);
                    GLES20.glEnableVertexAttribArray(videoPlaybackTexCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    // IMPORTANT:
                    // Notice here that the texture that we are binding is not the
                    // typical GL_TEXTURE_2D but instead the GL_TEXTURE_EXTERNAL_OES
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                            videoPlaybackTextureID[currentTarget]);
                    GLES20.glUniformMatrix4fv(videoPlaybackMVPMatrixHandle, 1,
                            false, modelViewProjectionVideo, 0);
                    GLES20.glUniform1i(videoPlaybackTexSamplerOESHandle, 0);

                    // Render
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX,
                            GLES20.GL_UNSIGNED_SHORT, quadIndices);

                    GLES20.glDisableVertexAttribArray(videoPlaybackVertexHandle);
                    GLES20.glDisableVertexAttribArray(videoPlaybackTexCoordHandle);

                    GLES20.glUseProgram(0);

                }

                // The following section renders the icons. The actual textures used
                // are loaded from the assets folder

                if ((currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.READY)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.REACHED_END)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.PAUSED)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.NOT_READY)
                        || (currentStatus[currentTarget] == VideoPlayerHelper.MEDIA_STATE.ERROR)) {
                    // If the movie is ready to be played, pause, has reached end or
                    // is not
                    // ready then we display one of the icons
                    float[] modelViewMatrixButton = Tool.convertPose2GLMatrix(
                            trackableResult.getPose()).getData();
                    float[] modelViewProjectionButton = new float[16];

                    GLES20.glDepthFunc(GLES20.GL_LEQUAL);

                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,
                            GLES20.GL_ONE_MINUS_SRC_ALPHA);

                    // The inacuracy of the rendering process in some devices means
                    // that
                    // even if we use the "Less or Equal" version of the depth
                    // function
                    // it is likely that we will get ugly artifacts
                    // That is the translation in the Z direction is slightly
                    // different
                    // Another posibility would be to use a depth func "ALWAYS" but
                    // that is typically not a good idea
                    Matrix
                            .translateM(
                                    modelViewMatrixButton,
                                    0,
                                    0.0f,
                                    0.0f,
                                    targetPositiveDimensions[currentTarget].getData()[1] / 10.98f);
                    Matrix
                            .scaleM(
                                    modelViewMatrixButton,
                                    0,
                                    (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f),
                                    (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f),
                                    (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f));
                    Matrix.multiplyMM(modelViewProjectionButton, 0,
                            mVuforiaAppSession.getProjectionMatrix().getData(), 0,
                            modelViewMatrixButton, 0);

                    GLES20.glUseProgram(keyframeShaderID);

                    GLES20.glVertexAttribPointer(keyframeVertexHandle, 3,
                            GLES20.GL_FLOAT, false, 0, quadVertices);
                    GLES20.glVertexAttribPointer(keyframeTexCoordHandle, 2,
                            GLES20.GL_FLOAT, false, 0, quadTexCoords);

                    GLES20.glEnableVertexAttribArray(keyframeVertexHandle);
                    GLES20.glEnableVertexAttribArray(keyframeTexCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    // Depending on the status in which we are we choose the
                    // appropriate
                    // texture to display. Notice that unlike the video these are
                    // regular
                    // GL_TEXTURE_2D textures
                    switch (currentStatus[currentTarget]) {
                        case READY:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(2).mTextureID[0]);
                            break;
                        case REACHED_END:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(2).mTextureID[0]);
                            break;
                        case PAUSED:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(2).mTextureID[0]);
                            break;
                        case NOT_READY:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(3).mTextureID[0]);
                            break;
                        case ERROR:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(4).mTextureID[0]);
                            break;
                        default:
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                                    mVideoTextures.get(3).mTextureID[0]);
                            break;
                    }
                    GLES20.glUniformMatrix4fv(keyframeMVPMatrixHandle, 1, false,
                            modelViewProjectionButton, 0);
                    GLES20.glUniform1i(keyframeTexSampler2DHandle, 0);

                    // Render
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX,
                            GLES20.GL_UNSIGNED_SHORT, quadIndices);

                    GLES20.glDisableVertexAttribArray(keyframeVertexHandle);
                    GLES20.glDisableVertexAttribArray(keyframeTexCoordHandle);

                    GLES20.glUseProgram(0);

                    // Finally we return the depth func to its original state
                    GLES20.glDepthFunc(GLES20.GL_LESS);
                    GLES20.glDisable(GLES20.GL_BLEND);
                }
            }

            Utils.checkGLError("VideoPlayback renderFrame");
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        Renderer.getInstance().end();

    }

    // Store the Player Helper object passed from the main activity
    public void setVideoPlayerHelper(int target, VideoPlayerHelper newVideoPlayerHelper) {
        mVideoPlayerHelper[target] = newVideoPlayerHelper;
    }

    public void requestLoad(int target, String movieName, int seekPosition,
                            boolean playImmediately) {
        mMovieName[target] = movieName;
        mSeekPosition[target] = seekPosition;
        mShouldPlayImmediately[target] = playImmediately;
        mLoadRequested[target] = true;
    }

    public boolean isTapOnScreenInsideTarget(int target, float x, float y) {
        // Here we calculate that the touch event is inside the target
        Vec3F intersection;
        // Vec3F lineStart = new Vec3F();
        // Vec3F lineEnd = new Vec3F();

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        intersection = Math.getPointToPlaneIntersection(Math
                        .Matrix44FInverse(mVuforiaAppSession.getProjectionMatrix()),
                modelViewMatrix[target], metrics.widthPixels, metrics.heightPixels,
                new Vec2F(x, y), new Vec3F(0, 0, 0), new Vec3F(0, 0, 1));

        // The target returns as pose the center of the trackable. The following
        // if-statement simply checks that the tap is within this range
        if ((intersection.getData()[0] >= -(targetPositiveDimensions[target]
                .getData()[0]))
                && (intersection.getData()[0] <= (targetPositiveDimensions[target]
                .getData()[0]))
                && (intersection.getData()[1] >= -(targetPositiveDimensions[target]
                .getData()[1]))
                && (intersection.getData()[1] <= (targetPositiveDimensions[target]
                .getData()[1]))) {
            mPaused = true;
            return true;
        } else {
            return false;
        }
    }


    void setVideoDimensions(int target, float videoWidth, float videoHeight,
                            float[] textureCoordMatrix) {
        // The quad originaly comes as a perfect square, however, the video
        // often has a different aspect ration such as 4:3 or 16:9,
        // To mitigate this we have two options:
        // 1) We can either scale the width (typically up)
        // 2) We can scale the height (typically down)
        // Which one to use is just a matter of preference. This example scales
        // the height down.
        // (see the render call in renderFrame)
        videoQuadAspectRatio[target] = videoHeight / videoWidth;

        float mtx[] = textureCoordMatrix;
        float tempUVMultRes[] = new float[2];

        if (target == VuforiaCordovaPlugin.NL) {
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedNL[0],
                    videoQuadTextureCoordsTransformedNL[1],
                    videoQuadTextureCoords[0], videoQuadTextureCoords[1], mtx);
            videoQuadTextureCoordsTransformedNL[0] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedNL[1] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedNL[2],
                    videoQuadTextureCoordsTransformedNL[3],
                    videoQuadTextureCoords[2], videoQuadTextureCoords[3], mtx);
            videoQuadTextureCoordsTransformedNL[2] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedNL[3] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedNL[4],
                    videoQuadTextureCoordsTransformedNL[5],
                    videoQuadTextureCoords[4], videoQuadTextureCoords[5], mtx);
            videoQuadTextureCoordsTransformedNL[4] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedNL[5] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedNL[6],
                    videoQuadTextureCoordsTransformedNL[7],
                    videoQuadTextureCoords[6], videoQuadTextureCoords[7], mtx);
            videoQuadTextureCoordsTransformedNL[6] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedNL[7] = tempUVMultRes[1];
        } else if (target == VuforiaCordovaPlugin.FR) {
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedFR[0],
                    videoQuadTextureCoordsTransformedFR[1],
                    videoQuadTextureCoords[0], videoQuadTextureCoords[1], mtx);
            videoQuadTextureCoordsTransformedFR[0] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedFR[1] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedFR[2],
                    videoQuadTextureCoordsTransformedFR[3],
                    videoQuadTextureCoords[2], videoQuadTextureCoords[3], mtx);
            videoQuadTextureCoordsTransformedFR[2] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedFR[3] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedFR[4],
                    videoQuadTextureCoordsTransformedFR[5],
                    videoQuadTextureCoords[4], videoQuadTextureCoords[5], mtx);
            videoQuadTextureCoordsTransformedFR[4] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedFR[5] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedFR[6],
                    videoQuadTextureCoordsTransformedFR[7],
                    videoQuadTextureCoords[6], videoQuadTextureCoords[7], mtx);
            videoQuadTextureCoordsTransformedFR[6] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedFR[7] = tempUVMultRes[1];
        }

        // textureCoordMatrix = mtx;
    }


    // Multiply the UV coordinates by the given transformation matrix
    float[] uvMultMat4f(float transformedU, float transformedV, float u,
                        float v, float[] pMat) {
        float x = pMat[0] * u + pMat[4] * v /* + pMat[ 8]*0.f */ + pMat[12]
                * 1.f;
        float y = pMat[1] * u + pMat[5] * v /* + pMat[ 9]*0.f */ + pMat[13]
                * 1.f;
        // float z = pMat[2]*u + pMat[6]*v + pMat[10]*0.f + pMat[14]*1.f; // We
        // dont need z and w so we comment them out
        // float w = pMat[3]*u + pMat[7]*v + pMat[11]*0.f + pMat[15]*1.f;

        float result[] = new float[2];
        // transformedU = x;
        // transformedV = y;
        result[0] = x;
        result[1] = y;
        return result;
    }


    void setStatus(int target, int value) {
        // Transform the value passed from java to our own values
        switch (value) {
            case 0:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.REACHED_END;
                break;
            case 1:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.PAUSED;
                break;
            case 2:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.STOPPED;
                break;
            case 3:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.PLAYING;
                break;
            case 4:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.READY;
                break;
            case 5:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.NOT_READY;
                break;
            case 6:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.ERROR;
                break;
            default:
                currentStatus[target] = VideoPlayerHelper.MEDIA_STATE.NOT_READY;
                break;
        }
    }

    boolean isTracking(int target) {
        return isTracking[target];
    }

    public void setObjectRotationAnimation(boolean active) {
        mRotate = active;
    }
}
