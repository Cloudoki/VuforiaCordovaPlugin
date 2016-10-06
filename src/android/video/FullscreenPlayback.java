/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.cloudoki.vuforiacordovaplugin.video;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

import com.cloudoki.vuforiacordovaplugin.utils.Logger;

import java.util.concurrent.locks.ReentrantLock;


public class FullscreenPlayback extends Activity implements MediaPlayer.OnPreparedListener,
        SurfaceHolder.Callback, MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
    private static final String TAG = "FullscreenPlayback";

    private VideoView mVideoView = null;
    private MediaPlayer mMediaPlayer = null;
    private SurfaceHolder mHolder = null;
    private MediaController mMediaController = null;
    private String mMovieName = "";
    private int mSeekPosition = 0;
    private int mRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    private GestureDetector mGestureDetector = null;
    private boolean mShouldPlayImmediately = false;
    private GestureDetector.SimpleOnGestureListener mSimpleListener = null;
    private ReentrantLock mMediaPlayerLock = null;
    private ReentrantLock mMediaControllerLock = null;
    LockedScreenReceiver mLockedScreenReceiver;


    // This is called when we need to prepare the view for the media player
    protected void prepareViewForMediaPlayer() {
        String package_name = getApplication().getPackageName();
        Resources resources = getApplication().getResources();
        // Create the view:
        mVideoView = (VideoView) findViewById(resources.getIdentifier("surface_view", "id", package_name));

        // The orientation was passed as an extra by the launching activity:
        setRequestedOrientation(mRequestedOrientation);

        mHolder = mVideoView.getHolder();
        mHolder.addCallback(this);
    }


    protected void onCreate(Bundle savedInstanceState) {
        Logger.d(TAG, "Fullscreen.onCreate");
        super.onCreate(savedInstanceState);

        String package_name = getApplication().getPackageName();
        Resources resources = getApplication().getResources();

        setContentView(resources.getIdentifier("fullscreen_layout", "layout", package_name));

        // Create the locks:
        mMediaControllerLock = new ReentrantLock();
        mMediaPlayerLock = new ReentrantLock();

        // Request a view to be used by the media player:
        prepareViewForMediaPlayer();

        // Collect all of the data passed by the launching activity:
        mSeekPosition = getIntent().getIntExtra("currentSeekPosition", 0);
        mMovieName = getIntent().getStringExtra("movieName");
        mRequestedOrientation = getIntent().getIntExtra("requestedOrientation",
                0);
        mShouldPlayImmediately = getIntent().getBooleanExtra(
                "shouldPlayImmediately", false);

        // Create a gesture detector that will handle single and double taps:
        mSimpleListener = new GestureDetector.SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(getApplicationContext(),
                mSimpleListener);

        // We assign the actions for the single and double taps:
        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            public boolean onDoubleTap(MotionEvent e) {
                return false;
            }


            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }


            public boolean onSingleTapConfirmed(MotionEvent e) {
                boolean result = false;
                mMediaControllerLock.lock();
                // This simply toggles the MediaController visibility:
                if (mMediaController != null) {
                    if (mMediaController.isShowing())
                        mMediaController.hide();
                    else
                        mMediaController.show();

                    result = true;
                }
                mMediaControllerLock.unlock();

                return result;
            }
        });

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        mLockedScreenReceiver = new LockedScreenReceiver();
        registerReceiver(mLockedScreenReceiver, filter);

    }


    // This is the call that actually creates the media player
    private void createMediaPlayer() {
        mMediaPlayerLock.lock();
        mMediaControllerLock.lock();

        try {
            // Create the MediaPlayer and its controller:
            mMediaPlayer = new MediaPlayer();
            mMediaController = new MediaController(this);

            // This example shows how to load the movie from the assets
            // folder of the app. However, if you would like to load the
            // movie from the SD card or from a network location, simply
            // comment the four lines below:
            AssetFileDescriptor afd = getAssets().openFd(mMovieName);
            mMediaPlayer.setDataSource(afd.getFileDescriptor(),
                    afd.getStartOffset(), afd.getLength());
            afd.close();

            // And uncomment this line:
            // mMediaPlayer.setDataSource("/sdcard/myMovie.m4v");

            mMediaPlayer.setDisplay(mHolder);
            mMediaPlayer.prepareAsync();
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        } catch (Exception e) {
            Logger.e(TAG,
                    "Error while creating the MediaPlayer: " + e.toString());

            // If something failed then prepare for termination:
            prepareForTermination();

            // Release the resources of the media player:
            destroyMediaPlayer();

            // Then terminate this activity:
            finish();
        }

        mMediaControllerLock.unlock();
        mMediaPlayerLock.unlock();
    }


    // Handle the touch event
    public boolean onTouchEvent(MotionEvent event) {
        // The touch event is actually handled by the gesture detector
        // so we just forward the event to it:
        return mGestureDetector.onTouchEvent(event);
    }


    // This is a callback we receive when the media player is ready to start
    // playing
    public void onPrepared(MediaPlayer mediaplayer) {
        // Logger.d( TAG, "Fullscreen.onPrepared");

        mMediaControllerLock.lock();
        mMediaPlayerLock.lock();

        if ((mMediaController != null) && (mVideoView != null)
                && (mMediaPlayer != null)) {
            if (mVideoView.getParent() != null) {
                // We attach the media controller to the player:
                mMediaController.setMediaPlayer(player_interface);

                // Add the media controller to the view:
                View anchorView = mVideoView.getParent() instanceof View ? (View) mVideoView
                        .getParent() : mVideoView;
                mMediaController.setAnchorView(anchorView);
                mVideoView.setMediaController(mMediaController);
                mMediaController.setEnabled(true);

                // Move to a given position:
                try {
                    mMediaPlayer.seekTo(mSeekPosition);
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    mMediaControllerLock.unlock();
                    Logger.e(TAG, "Could not seek to a position");
                }

                // If the client requests that we play immediately
                // we tell the media player to start:
                if (mShouldPlayImmediately) {
                    try {
                        mMediaPlayer.start();
                        mShouldPlayImmediately = false;
                    } catch (Exception e) {
                        mMediaPlayerLock.unlock();
                        mMediaControllerLock.unlock();
                        Logger.e(TAG, "Could not start playback");
                    }
                }

                // Show briefly the controls:
                mMediaController.show();
            }
        }

        mMediaPlayerLock.unlock();
        mMediaControllerLock.unlock();
    }


    // Called when we wish to release the resources of the media player
    private void destroyMediaPlayer() {
        // Release the Media Controller:
        mMediaControllerLock.lock();
        if (mMediaController != null) {
            mMediaController.removeAllViews();
            mMediaController = null;
        }
        mMediaControllerLock.unlock();

        // Release the MediaPlayer:
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Logger.e(TAG, "Could not stop playback");
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mMediaPlayerLock.unlock();
    }


    // Called when we wish to destroy the view used by the Media player
    private void destroyView() {
        // Release the View and the Holder:
        mVideoView = null;
        mHolder = null;
    }


    // Called when the app is destroyed
    protected void onDestroy() {
        // Logger.d( TAG, "Fullscreen.onDestroy");

        // Prepare the media player for termination:
        prepareForTermination();

        super.onDestroy();

        // Release the resources of the media player:
        destroyMediaPlayer();

        mMediaPlayerLock = null;
        mMediaControllerLock = null;
    }

    // Called when the app is resumed
    protected void onResume() {
        // Logger.d( TAG, "Fullscreen.onResume");
        super.onResume();

        // Prepare a view that the media player can use:
        prepareViewForMediaPlayer();

        if(mLockedScreenReceiver.wasLocked()) {
            createMediaPlayer();

            if(mMediaController != null)
                mMediaController.show();

            mLockedScreenReceiver.setLocked(false);
        }
    }


    // Called when the activity configuration has changed
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }


    // This is called when we should prepare the media player and the
    // activity for termination
    private void prepareForTermination() {
        // First we prepare the controller:
        mMediaControllerLock.lock();
        if (mMediaController != null) {
            mMediaController.hide();
            mMediaController.removeAllViews();
        }
        mMediaControllerLock.unlock();

        // Then the MediaPlayer:
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            // We store the position where it was currently playing:
            mSeekPosition = mMediaPlayer.getCurrentPosition();

            // We store the playback mode of the movie:
            boolean wasPlaying = mMediaPlayer.isPlaying();
            if (wasPlaying) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Logger.e(TAG, "Could not pause playback");
                }
            }

            // This activity was started for result, thus we need to return
            // whether it was playing and in which position:
            Intent i = new Intent();
            i.putExtra("movieName", mMovieName);
            i.putExtra("currentSeekPosition", mSeekPosition);
            i.putExtra("playing", wasPlaying);
            setResult(Activity.RESULT_OK, i);
        }
        mMediaPlayerLock.unlock();
    }


    public void onBackPressed() {
        // Request the media player to prepare for termination:
        prepareForTermination();
        super.onBackPressed();
    }


    // Called when the activity is paused
    protected void onPause() {
        // Logger.d( TAG, "Fullscreen.onPause");
        super.onPause();

        // We first prepare for termination:
        prepareForTermination();

        // Request the release of resource of the media player:
        destroyMediaPlayer();
    }


    // Called when the surface is changed
    public void surfaceCreated(SurfaceHolder holder) {
        // Request the creation of a media player:
        createMediaPlayer();
    }


    // Called when the surface is changed
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }


    // Called when the surface is destroyed
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    // The following are the predefined methods of the MediaPlayerController
    // We simply forward the values to/from the MediaPlayer
    private MediaController.MediaPlayerControl player_interface = new MediaController.MediaPlayerControl() {
        // Returns the current buffering percentage
        public int getBufferPercentage() {
            return 100;
        }


        // Returns the current seek position
        public int getCurrentPosition() {
            int result = 0;
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.getCurrentPosition();
            mMediaPlayerLock.unlock();
            return result;
        }


        // Returns the duration of the movie
        public int getDuration() {
            int result = 0;
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.getDuration();
            mMediaPlayerLock.unlock();
            return result;
        }


        // Returns whether the movie is currently playing
        public boolean isPlaying() {
            boolean result = false;

            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.isPlaying();
            mMediaPlayerLock.unlock();
            return result;
        }


        // Pauses the current playback
        public void pause() {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Logger.e(TAG, "Could not pause playback");
                }
            }
            mMediaPlayerLock.unlock();
        }


        // Seeks to the required position
        public void seekTo(int pos) {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.seekTo(pos);
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Logger.e(TAG, "Could not seek to position");
                }
            }
            mMediaPlayerLock.unlock();
        }


        // Starts the playback of the movie
        public void start() {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.start();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Logger.e(TAG, "Could not start playback");
                }
            }
            mMediaPlayerLock.unlock();
        }


        // Returns whether the movie can be paused
        public boolean canPause() {
            return true;
        }


        // Returns whether the movie can seek backwards
        public boolean canSeekBackward() {
            return true;
        }


        // Returns whether the movie can seek forwards
        public boolean canSeekForward() {
            return true;
        }


        public int getAudioSessionId() {
            return 0;
        }
    };


    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
    }


    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mp == mMediaPlayer) {
            String errorDescription;

            switch (what) {
                case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    errorDescription = "The video is streamed and its container is not valid for progressive playback";
                    break;

                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorDescription = "Media server died";
                    break;

                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorDescription = "Unspecified media player error";
                    break;

                default:
                    errorDescription = "Unknown error " + what;
                    break;
            }

            Logger.e(TAG, "Error while opening the file for fullscreen. "
                    + "Unloading the media player (" + errorDescription + ", "
                    + extra + ")");

            // If something failed then prepare for termination and
            // request a finish:
            prepareForTermination();

            // Release the resources of the media player:
            destroyMediaPlayer();

            // Then terminate this activity:
            finish();

            return true;
        }

        return false;
    }


    @Override
    public void onCompletion(MediaPlayer mp) {
        prepareForTermination();
        finish();
    }

    class LockedScreenReceiver extends BroadcastReceiver {

        boolean wasLocked = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                wasLocked = true;
            }
        }

        public boolean wasLocked()
        {
            return wasLocked;
        }

        public void setLocked(boolean isLocked)
        {
            wasLocked = isLocked;
        }

    }
}
