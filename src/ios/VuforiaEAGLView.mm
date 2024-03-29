/*===============================================================================
 Copyright (c) 2016 PTC Inc. All Rights Reserved.

 Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

 Vuforia is a trademark of PTC Inc., registered in the United States and other
 countries.
 ===============================================================================*/

#import <QuartzCore/QuartzCore.h>
#import <OpenGLES/ES2/gl.h>
#import <OpenGLES/ES2/glext.h>
#import <sys/time.h>

#import <Vuforia/Vuforia.h>
#import <Vuforia/State.h>
#import <Vuforia/Tool.h>
#import <Vuforia/Renderer.h>
#import <Vuforia/TrackableResult.h>
#import <Vuforia/VideoBackgroundConfig.h>
#import <Vuforia/ImageTarget.h>

#import "VuforiaEAGLView.h"
#import "Texture.h"
#import "VuforiaAppUtils.h"
#import "VuforiaAppShaderUtils.h"
// #import "Teapot.h"
#import "milestone.h"
#import "VuforiaMath.h"
#import "Quad.h"

#import "VuforiaViewController.h"


//******************************************************************************
// *** OpenGL ES thread safety ***
//
// OpenGL ES on iOS is not thread safe.  We ensure thread safety by following
// this procedure:
// 1) Create the OpenGL ES context on the main thread.
// 2) Start the Vuforia camera, which causes Vuforia to locate our EAGLView and start
//    the render thread.
// 3) Vuforia calls our renderFrameVuforia method periodically on the render thread.
//    The first time this happens, the defaultFramebuffer does not exist, so it
//    is created with a call to createFramebuffer.  createFramebuffer is called
//    on the main thread in order to safely allocate the OpenGL ES storage,
//    which is shared with the drawable layer.  The render (background) thread
//    is blocked during the call to createFramebuffer, thus ensuring no
//    concurrent use of the OpenGL ES context.
//
//******************************************************************************


namespace {
    // --- Data private to this unit ---

    // Teapot texture filenames
    const char* imageTextureFilenames[kNumImageAugmentationTextures] = {
        "/www/assets/milestone_texture.jpg"
    };

    // Model scale factor
    const float kObjectScaleNormal = 3.0f * 70;
    const float kObjectScaleOffTargetTracking = 12.0f;

    // Texture filenames (an Object3D object is created for each texture)
    const char* videoTextureFilenames[kNumVideoAugmentationTextures] = {
        "/www/assets/VideoPlayback/play.png",
        "/www/assets/VideoPlayback/busy.png",
        "/www/assets/VideoPlayback/error.png",
        "/www/assets/VideoPlayback/energylab25fps_nl.png",
        "/www/assets/VideoPlayback/energylab25fps_fr.png"
    };

    enum tagObjectIndex {
        OBJECT_PLAY_ICON,
        OBJECT_BUSY_ICON,
        OBJECT_ERROR_ICON,
        OBJECT_KEYFRAME_1,
        OBJECT_KEYFRAME_2,
    };

    const NSTimeInterval TRACKING_LOST_TIMEOUT = 2.0f;

    // Playback icon scale factors
    const float SCALE_ICON = 2.0f;

    // Video quad texture coordinates
    const GLfloat videoQuadTextureCoords[] = {
        0.0, 1.0,
        1.0, 1.0,
        1.0, 0.0,
        0.0, 0.0,
    };

    struct tagVideoData {
        // Needed to calculate whether a screen tap is inside the target
        Vuforia::Matrix44F modelViewMatrix;

        // Trackable dimensions
        Vuforia::Vec2F targetPositiveDimensions;

        // Currently active flag
        BOOL isActive;
    } videoData[kNumVideoTargets];

    int touchedTarget = 0;

    VuforiaViewController *vuforiaViewController;
    
    NSString * _detectedTarget = @"";
    BOOL pausedByTap = NO;
    BOOL rotating = YES;
    float angle = 0.0f;
}


@interface VuforiaEAGLView (PrivateMethods)

- (void)initShaders;
- (void)createFramebuffer;
- (void)deleteFramebuffer;
- (void)setFramebuffer;
- (BOOL)presentFramebuffer;

@end


@implementation VuforiaEAGLView

@synthesize vapp = vapp;

// You must implement this method, which ensures the view's underlying layer is
// of type CAEAGLLayer
+ (Class)layerClass
{
    return [CAEAGLLayer class];
}


//------------------------------------------------------------------------------
#pragma mark - Lifecycle

- (id)initWithFrame:(CGRect)frame appSession:(VuforiaAppSession *) app controller:(VuforiaViewController *) viewController
{
    self = [super initWithFrame:frame];

    if (self) {
        vapp = app;
        
        vuforiaViewController = viewController;
        
        // Enable retina mode if available on this device
        if (YES == [vapp isRetinaDisplay]) {
            [self setContentScaleFactor:[UIScreen mainScreen].nativeScale];
        }

        // Load the augmentation textures for the objects/images
        for (int i = 0; i < kNumImageAugmentationTextures; ++i) {
            imageAugmentationTexture[i] = [[Texture alloc] initWithImageFile:[NSString stringWithCString:imageTextureFilenames[i] encoding:NSASCIIStringEncoding]];
        }
        for (int i = 0; i < kNumVideoAugmentationTextures; ++i) {
            videoAugmentationTexture[i] = [[Texture alloc] initWithImageFile:[NSString stringWithCString:videoTextureFilenames[i] encoding:NSASCIIStringEncoding]];
        }

        // Create the OpenGL ES context
        context = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];

        // The EAGLContext must be set for each thread that wishes to use it.
        // Set it the first time this method is called (on the main thread)
        if (context != [EAGLContext currentContext]) {
            [EAGLContext setCurrentContext:context];
        }

        // Generate the OpenGL ES texture and upload the texture data for use
        // when rendering the augmentation
        for (int i = 0; i < kNumImageAugmentationTextures; ++i) {
            GLuint textureID;
            glGenTextures(1, &textureID);
            [imageAugmentationTexture[i] setTextureID:textureID];
            glBindTexture(GL_TEXTURE_2D, textureID);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, [imageAugmentationTexture[i] width], [imageAugmentationTexture[i] height], 0, GL_RGBA, GL_UNSIGNED_BYTE, (GLvoid*)[imageAugmentationTexture[i] pngData]);
        }

        for (int i = 0; i < kNumVideoAugmentationTextures; ++i) {
            GLuint textureID;
            glGenTextures(1, &textureID);
            [videoAugmentationTexture[i] setTextureID:textureID];
            glBindTexture(GL_TEXTURE_2D, textureID);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, [videoAugmentationTexture[i] width], [videoAugmentationTexture[i] height], 0, GL_RGBA, GL_UNSIGNED_BYTE, (GLvoid*)[videoAugmentationTexture[i] pngData]);

            // Set appropriate texture parameters (for NPOT textures)
            if (OBJECT_KEYFRAME_1 <= i) {
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            }
        }

        offTargetTrackingEnabled = NO;
        appRenderer = [[AppRenderer alloc]initWithAppRendererControl:self deviceMode:Vuforia::Device::MODE_AR stereo:false];

        [self loadBuildingsModel];
        [self initShaders];

        // we initialize the rendering method of the AppRenderer
        [appRenderer initRendering];
    }

    return self;
}

- (void) willPlayVideoFullScreen:(BOOL) fullScreen {
    playVideoFullScreen = fullScreen;
}

- (void) prepare {
    // For each target, create a VideoPlayerHelper object and zero the
    // target dimensions
    // For each target, create a VideoPlayerHelper object and zero the
    // target dimensions
    for (int i = 0; i < kNumVideoTargets; ++i) {
        videoPlayerHelper[i] = [[VideoPlayerHelper alloc] initWithRootViewController:vuforiaViewController];
        videoData[i].targetPositiveDimensions.data[0] = 0.0f;
        videoData[i].targetPositiveDimensions.data[1] = 0.0f;
    }

    // Start video playback from the current position (the beginning) on the
    // first run of the app
    for (int i = 0; i < kNumVideoTargets; ++i) {
        videoPlaybackTime[i] = VIDEO_PLAYBACK_CURRENT_POSITION;
    }

    // For each video-augmented target
    for (int i = 0; i < kNumVideoTargets; ++i) {
        // Load a local file for playback and resume playback if video was
        // playing when the app went into the background
        VideoPlayerHelper* player = [self getVideoPlayerHelper:i];
        NSString* filename;

        switch (i) {
            case 0:
                filename = @"www/assets/VideoPlayback/energylab25fps_nl.mp4";
                break;
            default:
                filename = @"www/assets/VideoPlayback/energylab25fps_fr.mp4";
                break;
        }

        if (NO == [player load:filename playImmediately:NO fromPosition:videoPlaybackTime[i]]) {
            NSLog(@"Failed to load media");
        }
    }
}

- (void) dismiss {
    for (int i = 0; i < kNumVideoTargets; ++i) {
        [videoPlayerHelper[i] unload];
        videoPlayerHelper[i] = nil;
    }
}

- (void)dealloc
{
    [self deleteFramebuffer];

    // Tear down context
    if ([EAGLContext currentContext] == context) {
        [EAGLContext setCurrentContext:nil];
    }

    for (int i = 0; i < kNumImageAugmentationTextures; ++i) {
        imageAugmentationTexture[i] = nil;
    }
    for (int i = 0; i < kNumVideoAugmentationTextures; ++i) {
        videoAugmentationTexture[i] = nil;
    }
    for (int i = 0; i < kNumVideoTargets; ++i) {
        videoPlayerHelper[i] = nil;
    }
    [super dealloc];
}


- (void)finishOpenGLESCommands
{
    // Called in response to applicationWillResignActive.  The render loop has
    // been stopped, so we now make sure all OpenGL ES commands complete before
    // we (potentially) go into the background
    if (context) {
        [EAGLContext setCurrentContext:context];
        glFinish();
    }
}


- (void)freeOpenGLESResources
{
    // Called in response to applicationDidEnterBackground.  Free easily
    // recreated OpenGL ES resources
    [self deleteFramebuffer];
    glFinish();
}

- (void) setOffTargetTrackingMode:(BOOL) enabled {
    offTargetTrackingEnabled = enabled;
}

- (void) loadBuildingsModel {
    buildingModel = [[SampleApplication3DModel alloc] initWithTxtResourceName:@"buildings"];
    [buildingModel read];
}


//------------------------------------------------------------------------------
#pragma mark - User interaction

- (bool) handleTouchPoint:(CGPoint) point {
    // Store the current touch location
    touchLocation_X = point.x;
    touchLocation_Y = point.y;

    // Determine which target was touched (if no target was touch, touchedTarget
    // will be -1)
    touchedTarget = [self tapInsideTargetWithID];

    // Ignore touches when videoPlayerHelper is playing in fullscreen mode
    if (-1 != touchedTarget && PLAYING_FULLSCREEN != [videoPlayerHelper[touchedTarget] getStatus]) {
        // Get the state of the video player for the target the user touched
        MEDIA_STATE mediaState = [videoPlayerHelper[touchedTarget] getStatus];

        // If any on-texture video is playing, pause it
        for (int i = 0; i < kNumVideoTargets; ++i) {
            if (PLAYING == [videoPlayerHelper[i] getStatus]) {
                [videoPlayerHelper[i] pause];
                pausedByTap = YES; // paused by a tap
            }
        }

#ifdef EXAMPLE_CODE_REMOTE_FILE
        // With remote files, single tap starts playback using the native player
        if (ERROR != mediaState && NOT_READY != mediaState) {
            // Play the video
            NSLog(@"Playing video with native player");
            [videoPlayerHelper[touchedTarget] play:YES fromPosition:VIDEO_PLAYBACK_CURRENT_POSITION];
        }
#else
        // For the target the user touched
        if (ERROR != mediaState && NOT_READY != mediaState && PLAYING != mediaState) {
            // Play the video
            NSLog(@"Playing video with on-texture player");
            [videoPlayerHelper[touchedTarget] play:playVideoFullScreen fromPosition:VIDEO_PLAYBACK_CURRENT_POSITION];
        }
#endif
        return true;
    } else {
        return false;
    }
}
- (void) preparePlayers {
    [self prepare];
}


- (void) dismissPlayers {
    [self dismiss];
}



// Determine whether a screen tap is inside the target
- (int)tapInsideTargetWithID
{
    Vuforia::Vec3F intersection, lineStart, lineEnd;
    // Get the current projection matrix
    Vuforia::Matrix44F projectionMatrix = [vapp projectionMatrix];
    Vuforia::Matrix44F inverseProjMatrix = VuforiaMath::Matrix44FInverse(projectionMatrix);
    CGRect rect = [self bounds];
    int touchInTarget = -1;

    // ----- Synchronise data access -----
    [dataLock lock];

    // The target returns as pose the centre of the trackable.  Thus its
    // dimensions go from -width / 2 to width / 2 and from -height / 2 to
    // height / 2.  The following if statement simply checks that the tap is
    // within this range
    for (int i = 0; i < kNumVideoTargets; ++i) {
        VuforiaMath::projectScreenPointToPlane(inverseProjMatrix, videoData[i].modelViewMatrix, rect.size.width, rect.size.height,
                                              Vuforia::Vec2F(touchLocation_X, touchLocation_Y), Vuforia::Vec3F(0, 0, 0), Vuforia::Vec3F(0, 0, 1), intersection, lineStart, lineEnd);

        if ((intersection.data[0] >= -videoData[i].targetPositiveDimensions.data[0]) && (intersection.data[0] <= videoData[i].targetPositiveDimensions.data[0]) &&
            (intersection.data[1] >= -videoData[i].targetPositiveDimensions.data[1]) && (intersection.data[1] <= videoData[i].targetPositiveDimensions.data[1])) {
            // The tap is only valid if it is inside an active target
            if (YES == videoData[i].isActive) {
                touchInTarget = i;
                break;
            }
        }
    }

    [dataLock unlock];
    // ----- End synchronise data access -----

    return touchInTarget;
}

// Get a pointer to a VideoPlayerHelper object held by this EAGLView
- (VideoPlayerHelper*)getVideoPlayerHelper:(int)index
{
    return videoPlayerHelper[index];
}


//------------------------------------------------------------------------------
#pragma mark - UIGLViewProtocol methods

// Draw the current frame using OpenGL
//
// This method is called by Vuforia when it wishes to render the current frame to
// the screen.
//
// *** Vuforia will call this method periodically on a background thread ***
- (void)renderFrameVuforia
{
    if (! vapp.cameraIsStarted) {
        return;
    }
    
    if([_detectedTarget compare:@"tombstone" options:NSCaseInsensitiveSearch] == NSOrderedSame) {
        [appRenderer renderFrameVuforia];
    } else if([_detectedTarget compare:@"tia" options:NSCaseInsensitiveSearch] == NSOrderedSame) {
        [self renderVideoFrameVuforia];
    } else {
        [self setFramebuffer];
        
        // Clear colour and depth buffers
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // Render video background and retrieve tracking state
        Vuforia::State state = Vuforia::Renderer::getInstance().begin();
        Vuforia::Renderer::getInstance().drawVideoBackground();
        Vuforia::Renderer::getInstance().end();
        
        // ----- Synchronise data access -----
        [dataLock lock];
        // Assume all targets are inactive (used when determining tap locations)
        for (int i = 0; i < kNumVideoTargets; ++i) {
            videoData[i].isActive = NO;
        }
        
        // If a video is playing on texture and we have lost tracking, create a
        // timer on the main thread that will pause video playback after
        // TRACKING_LOST_TIMEOUT seconds
        for (int i = 0; i < kNumVideoTargets; ++i) {
            if (nil == trackingLostTimer && NO == videoData[i].isActive && PLAYING == [videoPlayerHelper[i] getStatus]) {
                [self performSelectorOnMainThread:@selector(createTrackingLostTimer) withObject:nil waitUntilDone:YES];
                break;
            }
        }
        
        if(pausedByTap) {
            pausedByTap = NO; // reset value to play again on detection autoplay
        }
        [dataLock unlock];
        // ----- End synchronise data access -----
        
        [self presentFramebuffer];
    }
}

- (void)renderVideoFrameVuforia {
    [self setFramebuffer];

    // Clear colour and depth buffers
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Begin Vuforia rendering for this frame, retrieving the tracking state
    Vuforia::State state = Vuforia::Renderer::getInstance().begin();

    // Render the video background
    Vuforia::Renderer::getInstance().drawVideoBackground();

    glEnable(GL_DEPTH_TEST);

    // We must detect if background reflection is active and adjust the culling
    // direction.  If the reflection is active, this means the pose matrix has
    // been reflected as well, therefore standard counter clockwise face culling
    // will result in "inside out" models
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);

    if(Vuforia::Renderer::getInstance().getVideoBackgroundConfig().mReflection == Vuforia::VIDEO_BACKGROUND_REFLECTION_ON) {
        // Front camera
        glFrontFace(GL_CW);
    }
    else {
        // Back camera
        glFrontFace(GL_CCW);
    }

    // Get the active trackables
    int numActiveTrackables = state.getNumTrackableResults();

    // ----- Synchronise data access -----
    [dataLock lock];

    // Assume all targets are inactive (used when determining tap locations)
    for (int i = 0; i < kNumVideoTargets; ++i) {
        videoData[i].isActive = NO;
    }

    // Set the viewport
    glViewport(vapp.viewport.posX, vapp.viewport.posY, vapp.viewport.sizeX, vapp.viewport.sizeY);
    
    // load values set in plugin
    NSUserDefaults * defaults = [NSUserDefaults standardUserDefaults];
    NSString * language = [defaults objectForKey:@"lang"];
    BOOL autoplay = [defaults boolForKey:@"playOnDetection"];

    // Did we find any trackables this frame?
    for (int i = 0; i < numActiveTrackables; ++i) {
        // Get the trackable
        const Vuforia::TrackableResult* trackableResult = state.getTrackableResult(i);
        const Vuforia::ImageTarget& imageTarget = (const Vuforia::ImageTarget&) trackableResult->getTrackable();

        if (!strcmp(imageTarget.getName(), "tia")) {
            // VideoPlayerHelper to use for current target
            int playerIndex = 0;    // default language

            if ([language isEqualToString:@"fr"])
            {
                playerIndex = 1;
            }

            // Mark this video (target) as active
            videoData[playerIndex].isActive = YES;

            // Get the target size (used to determine if taps are within the target)
            if (0.0f == videoData[playerIndex].targetPositiveDimensions.data[0] ||
                0.0f == videoData[playerIndex].targetPositiveDimensions.data[1]) {
                const Vuforia::ImageTarget& imageTarget = (const Vuforia::ImageTarget&) trackableResult->getTrackable();

                Vuforia::Vec3F size = imageTarget.getSize();
                videoData[playerIndex].targetPositiveDimensions.data[0] = size.data[0];
                videoData[playerIndex].targetPositiveDimensions.data[1] = size.data[1];

                // The pose delivers the centre of the target, thus the dimensions
                // go from -width / 2 to width / 2, and -height / 2 to height / 2
                videoData[playerIndex].targetPositiveDimensions.data[0] /= 2.0f;
                videoData[playerIndex].targetPositiveDimensions.data[1] /= 2.0f;
            }

            // Get the current trackable pose
            const Vuforia::Matrix34F& trackablePose = trackableResult->getPose();

            // This matrix is used to calculate the location of the screen tap
            videoData[playerIndex].modelViewMatrix = Vuforia::Tool::convertPose2GLMatrix(trackablePose);

            float aspectRatio;
            const GLvoid* texCoords;
            GLuint frameTextureID = 0;
            BOOL displayVideoFrame = YES;

            // Retain value between calls
            static GLuint videoTextureID[kNumVideoTargets] = {0};

            MEDIA_STATE currentStatus = [videoPlayerHelper[playerIndex] getStatus];

            // NSLog(@"MEDIA_STATE for %d is %d", playerIndex, currentStatus);

            // --- INFORMATION ---
            // One could trigger automatic playback of a video at this point.  This
            // could be achieved by calling the play method of the VideoPlayerHelper
            // object if currentStatus is not PLAYING.  You should also call
            // getStatus again after making the call to play, in order to update the
            // value held in currentStatus.
            // --- END INFORMATION ---
            
            
            
            // uncomment to play automatically
            if(currentStatus != PLAYING && autoplay && !pausedByTap) {
                [videoPlayerHelper[playerIndex] play:NO fromPosition:VIDEO_PLAYBACK_CURRENT_POSITION];
                // update status after calling play
                currentStatus = [videoPlayerHelper[playerIndex] getStatus];
            }

            switch (currentStatus) {
                case PLAYING: {
                    // If the tracking lost timer is scheduled, terminate it
                    if (nil != trackingLostTimer) {
                        // Timer termination must occur on the same thread on which
                        // it was installed
                        [self performSelectorOnMainThread:@selector(terminateTrackingLostTimer) withObject:nil waitUntilDone:YES];
                    }

                    // Upload the decoded video data for the latest frame to OpenGL
                    // and obtain the video texture ID
                    GLuint videoTexID = [videoPlayerHelper[playerIndex] updateVideoData];

                    if (0 == videoTextureID[playerIndex]) {
                        videoTextureID[playerIndex] = videoTexID;
                    }

                    // Fallthrough
                }
                case PAUSED:
                    if (0 == videoTextureID[playerIndex]) {
                        // No video texture available, display keyframe
                        displayVideoFrame = NO;
                    }
                    else {
                        // Display the texture most recently returned from the call
                        // to [videoPlayerHelper updateVideoData]
                        frameTextureID = videoTextureID[playerIndex];
                    }

                    break;

                default:
                    videoTextureID[playerIndex] = 0;
                    displayVideoFrame = NO;
                    break;
            }

            if (YES == displayVideoFrame) {
                // ---- Display the video frame -----
                aspectRatio = (float)[videoPlayerHelper[playerIndex] getVideoHeight] / (float)[videoPlayerHelper[playerIndex] getVideoWidth];
                texCoords = videoQuadTextureCoords;
            }
            else {
                // ----- Display the keyframe -----
                Texture* t = videoAugmentationTexture[OBJECT_KEYFRAME_1 + playerIndex];
                frameTextureID = [t textureID];
                aspectRatio = (float)[t height] / (float)[t width];
                texCoords = quadTexCoords;
            }

            // Get the current projection matrix
            Vuforia::Matrix44F projMatrix = vapp.projectionMatrix;

            // If the current status is valid (not NOT_READY or ERROR), render the
            // video quad with the texture we've just selected
            if (NOT_READY != currentStatus) {
                // Convert trackable pose to matrix for use with OpenGL
                Vuforia::Matrix44F modelViewMatrixVideo = Vuforia::Tool::convertPose2GLMatrix(trackablePose);
                Vuforia::Matrix44F modelViewProjectionVideo;

                //            SampleApplicationUtils::translatePoseMatrix(0.0f, 0.0f, videoData[playerIndex].targetPositiveDimensions.data[0],
                //                                             &modelViewMatrixVideo.data[0]);

                VuforiaAppUtils::scalePoseMatrix(videoData[playerIndex].targetPositiveDimensions.data[0],
                                                        videoData[playerIndex].targetPositiveDimensions.data[0] * aspectRatio,
                                                        videoData[playerIndex].targetPositiveDimensions.data[0],
                                                        &modelViewMatrixVideo.data[0]);

                VuforiaAppUtils::multiplyMatrix(projMatrix.data,
                                                       &modelViewMatrixVideo.data[0] ,
                                                       &modelViewProjectionVideo.data[0]);

                glUseProgram(shaderProgramID);

                glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0, quadVertices);
                glVertexAttribPointer(normalHandle, 3, GL_FLOAT, GL_FALSE, 0, quadNormals);
                glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

                glEnableVertexAttribArray(vertexHandle);
                glEnableVertexAttribArray(normalHandle);
                glEnableVertexAttribArray(textureCoordHandle);

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, frameTextureID);
                glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE, (GLfloat*)&modelViewProjectionVideo.data[0]);
                glUniform1i(texSampler2DHandle, 0 /*GL_TEXTURE0*/);
                glDrawElements(GL_TRIANGLES, kNumQuadIndices, GL_UNSIGNED_SHORT, quadIndices);

                glDisableVertexAttribArray(vertexHandle);
                glDisableVertexAttribArray(normalHandle);
                glDisableVertexAttribArray(textureCoordHandle);

                glUseProgram(0);
            }

            // If the current status is not PLAYING, render an icon
            if (PLAYING != currentStatus) {
                GLuint iconTextureID;

                switch (currentStatus) {
                    case READY:
                    case REACHED_END:
                    case PAUSED:
                    case STOPPED: {
                        // ----- Display play icon -----
                        iconTextureID = [videoAugmentationTexture[OBJECT_PLAY_ICON] textureID];
                        break;
                    }

                    case ERROR: {
                        // ----- Display error icon -----
                        iconTextureID = [videoAugmentationTexture[OBJECT_ERROR_ICON] textureID];
                        break;
                    }

                    default: {
                        // ----- Display busy icon -----
                        iconTextureID = [videoAugmentationTexture[OBJECT_BUSY_ICON] textureID];
                        break;
                    }
                }

                // Convert trackable pose to matrix for use with OpenGL
                Vuforia::Matrix44F modelViewMatrixButton = Vuforia::Tool::convertPose2GLMatrix(trackablePose);
                Vuforia::Matrix44F modelViewProjectionButton;

                //SampleApplicationUtils::translatePoseMatrix(0.0f, 0.0f, videoData[playerIndex].targetPositiveDimensions.data[1] / SCALE_ICON_TRANSLATION, &modelViewMatrixButton.data[0]);
                VuforiaAppUtils::translatePoseMatrix(0.0f, 0.0f, 5.0f, &modelViewMatrixButton.data[0]);

                VuforiaAppUtils::scalePoseMatrix(videoData[playerIndex].targetPositiveDimensions.data[1] / SCALE_ICON,
                                                        videoData[playerIndex].targetPositiveDimensions.data[1] / SCALE_ICON,
                                                        videoData[playerIndex].targetPositiveDimensions.data[1] / SCALE_ICON,
                                                        &modelViewMatrixButton.data[0]);

                VuforiaAppUtils::multiplyMatrix(projMatrix.data,
                                                       &modelViewMatrixButton.data[0] ,
                                                       &modelViewProjectionButton.data[0]);

                glDepthFunc(GL_LEQUAL);

                glUseProgram(shaderProgramID);

                glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0, quadVertices);
                glVertexAttribPointer(normalHandle, 3, GL_FLOAT, GL_FALSE, 0, quadNormals);
                glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, quadTexCoords);

                glEnableVertexAttribArray(vertexHandle);
                glEnableVertexAttribArray(normalHandle);
                glEnableVertexAttribArray(textureCoordHandle);

                // Blend the icon over the background
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, iconTextureID);
                glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE, (GLfloat*)&modelViewProjectionButton.data[0] );
                glDrawElements(GL_TRIANGLES, kNumQuadIndices, GL_UNSIGNED_SHORT, quadIndices);

                glDisable(GL_BLEND);

                glDisableVertexAttribArray(vertexHandle);
                glDisableVertexAttribArray(normalHandle);
                glDisableVertexAttribArray(textureCoordHandle);

                glUseProgram(0);

                glDepthFunc(GL_LESS);
            }
        }

        VuforiaAppUtils::checkGlError("VideoPlayback renderFrameVuforia");
    }

    // --- INFORMATION ---
    // One could pause automatic playback of a video at this point.  Simply call
    // the pause method of the VideoPlayerHelper object without setting the
    // timer (as below).
    // --- END INFORMATION ---

    // If a video is playing on texture and we have lost tracking, create a
    // timer on the main thread that will pause video playback after
    // TRACKING_LOST_TIMEOUT seconds
    for (int i = 0; i < kNumVideoTargets; ++i) {
        if (nil == trackingLostTimer && NO == videoData[i].isActive && PLAYING == [videoPlayerHelper[i] getStatus]) {
            [self performSelectorOnMainThread:@selector(createTrackingLostTimer) withObject:nil waitUntilDone:YES];
            break;
        }
    }

    [dataLock unlock];
    // ----- End synchronise data access -----

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    Vuforia::Renderer::getInstance().end();
    [self presentFramebuffer];

}

// Create the tracking lost timer
- (void)createTrackingLostTimer
{
    trackingLostTimer = [NSTimer scheduledTimerWithTimeInterval:TRACKING_LOST_TIMEOUT target:self selector:@selector(trackingLostTimerFired:) userInfo:nil repeats:NO];
}


// Terminate the tracking lost timer
- (void)terminateTrackingLostTimer
{
    [trackingLostTimer invalidate];
    trackingLostTimer = nil;
}


// Tracking lost timer fired, pause video playback
- (void)trackingLostTimerFired:(NSTimer*)timer
{
    // Tracking has been lost for TRACKING_LOST_TIMEOUT seconds, pause playback
    // (we can safely do this on all our VideoPlayerHelpers objects)
    for (int i = 0; i < kNumVideoTargets; ++i) {
        [videoPlayerHelper[i] pause];
        pausedByTap = NO; // paused automatically
    }
    trackingLostTimer = nil;
}

- (void) renderFrameWithState:(const Vuforia::State&) state projectMatrix:(Vuforia::Matrix44F&) projectionMatrix {
    [self setFramebuffer];

    // Clear colour and depth buffers
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Render video background and retrieve tracking state
    [appRenderer renderVideoBackground];

    glEnable(GL_DEPTH_TEST);
    // We must detect if background reflection is active and adjust the culling direction.
    // If the reflection is active, this means the pose matrix has been reflected as well,
    // therefore standard counter clockwise face culling will result in "inside out" models.
    if (offTargetTrackingEnabled) {
        glDisable(GL_CULL_FACE);
    } else {
        glEnable(GL_CULL_FACE);
    }
    glCullFace(GL_BACK);
    if(Vuforia::Renderer::getInstance().getVideoBackgroundConfig().mReflection == Vuforia::VIDEO_BACKGROUND_REFLECTION_ON)
        glFrontFace(GL_CW);  //Front camera
    else
        glFrontFace(GL_CCW);   //Back camera

    for (int i = 0; i < state.getNumTrackableResults(); ++i) {
        // Get the trackable
        const Vuforia::TrackableResult* result = state.getTrackableResult(i);
        const Vuforia::Trackable& trackable = result->getTrackable();

        Vuforia::Matrix44F modelViewMatrix = Vuforia::Tool::convertPose2GLMatrix(result->getPose());

        // OpenGL 2
        Vuforia::Matrix44F modelViewProjection;
        

        if (offTargetTrackingEnabled) {
            VuforiaAppUtils::rotatePoseMatrix(90, 1, 0, 0,&modelViewMatrix.data[0]);
            VuforiaAppUtils::scalePoseMatrix(kObjectScaleOffTargetTracking, kObjectScaleOffTargetTracking, kObjectScaleOffTargetTracking, &modelViewMatrix.data[0]);
        } else {
            VuforiaAppUtils::translatePoseMatrix(0.0f, 0.0f, kObjectScaleNormal, &modelViewMatrix.data[0]);
            
            VuforiaAppUtils::rotatePoseMatrix(-90, -1, 0, 0,&modelViewMatrix.data[0]);
            
            if(rotating) {
                angle -= 1.0f;
                VuforiaAppUtils::rotatePoseMatrix(angle, 0, 1, 0,&modelViewMatrix.data[0]);
            }
            
            VuforiaAppUtils::scalePoseMatrix(kObjectScaleNormal, kObjectScaleNormal, kObjectScaleNormal, &modelViewMatrix.data[0]);
        }

        VuforiaAppUtils::multiplyMatrix(&projectionMatrix.data[0], &modelViewMatrix.data[0], &modelViewProjection.data[0]);

        glUseProgram(shaderProgramID);

        if (offTargetTrackingEnabled) {
            glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)buildingModel.vertices);
            glVertexAttribPointer(normalHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)buildingModel.normals);
            glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)buildingModel.texCoords);
        } else {
            glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)milestoneVerts);
            glVertexAttribPointer(normalHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)milestoneNormals);
            glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)milestoneTexCoords);
        }

        glEnableVertexAttribArray(vertexHandle);
        glEnableVertexAttribArray(normalHandle);
        glEnableVertexAttribArray(textureCoordHandle);

        // Choose the texture based on the target name
        if (!strcmp(trackable.getName(), "tombstone")) {
            int targetIndex = 0; // "milestone"
    //        if (!strcmp(trackable.getName(), "chips"))
    //            targetIndex = 1;
    //        else if (!strcmp(trackable.getName(), "tarmac"))
    //            targetIndex = 2;

            glActiveTexture(GL_TEXTURE0);
            // disable face culling to try and fix mesh missing triangles
            glDisable(GL_CULL_FACE);

            if (offTargetTrackingEnabled) {
                glBindTexture(GL_TEXTURE_2D, imageAugmentationTexture[3].textureID);
            } else {
                glBindTexture(GL_TEXTURE_2D, imageAugmentationTexture[targetIndex].textureID);
            }
            glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE, (const GLfloat*)&modelViewProjection.data[0]);
            glUniform1i(texSampler2DHandle, 0 /*GL_TEXTURE0*/);

            if (offTargetTrackingEnabled) {
                glDrawArrays(GL_TRIANGLES, 0, (int)buildingModel.numVertices);
            } else {
                // glDrawElements(GL_TRIANGLES, NUM_TEAPOT_OBJECT_INDEX, GL_UNSIGNED_SHORT, (const GLvoid*)teapotIndices);
                // draw data
                glDrawArrays(GL_TRIANGLES, 0, (int)milestoneNumVerts);
            }
        }

        glDisableVertexAttribArray(vertexHandle);
        glDisableVertexAttribArray(normalHandle);
        glDisableVertexAttribArray(textureCoordHandle);

        VuforiaAppUtils::checkGlError("EAGLView renderFrameVuforia");
    }

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    [self presentFramebuffer];
}

//------------------------------------------------------------------------------
#pragma mark - OpenGL ES management

- (void)initShaders
{
    shaderProgramID = [VuforiaAppShaderUtils createProgramWithVertexShaderFileName:@"Simple.vertsh"
                                                            fragmentShaderFileName:@"Simple.fragsh"];

    if (0 < shaderProgramID) {
        vertexHandle = glGetAttribLocation(shaderProgramID, "vertexPosition");
        normalHandle = glGetAttribLocation(shaderProgramID, "vertexNormal");
        textureCoordHandle = glGetAttribLocation(shaderProgramID, "vertexTexCoord");
        mvpMatrixHandle = glGetUniformLocation(shaderProgramID, "modelViewProjectionMatrix");
        texSampler2DHandle  = glGetUniformLocation(shaderProgramID,"texSampler2D");
    }
    else {
        NSLog(@"Could not initialise augmentation shader");
    }
}


- (void)createFramebuffer
{
    if (context) {
        // Create default framebuffer object
        glGenFramebuffers(1, &defaultFramebuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, defaultFramebuffer);

        // Create colour renderbuffer and allocate backing store
        glGenRenderbuffers(1, &colorRenderbuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, colorRenderbuffer);

        // Allocate the renderbuffer's storage (shared with the drawable object)
        [context renderbufferStorage:GL_RENDERBUFFER fromDrawable:(CAEAGLLayer*)self.layer];
        GLint framebufferWidth;
        GLint framebufferHeight;
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &framebufferWidth);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &framebufferHeight);

        // Create the depth render buffer and allocate storage
        glGenRenderbuffers(1, &depthRenderbuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, framebufferWidth, framebufferHeight);

        // Attach colour and depth render buffers to the frame buffer
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorRenderbuffer);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRenderbuffer);

        // Leave the colour render buffer bound so future rendering operations will act on it
        glBindRenderbuffer(GL_RENDERBUFFER, colorRenderbuffer);
    }
}


- (void)deleteFramebuffer
{
    if (context) {
        [EAGLContext setCurrentContext:context];

        if (defaultFramebuffer) {
            glDeleteFramebuffers(1, &defaultFramebuffer);
            defaultFramebuffer = 0;
        }

        if (colorRenderbuffer) {
            glDeleteRenderbuffers(1, &colorRenderbuffer);
            colorRenderbuffer = 0;
        }

        if (depthRenderbuffer) {
            glDeleteRenderbuffers(1, &depthRenderbuffer);
            depthRenderbuffer = 0;
        }
    }
}


- (void)setFramebuffer
{
    // The EAGLContext must be set for each thread that wishes to use it.  Set
    // it the first time this method is called (on the render thread)
    if (context != [EAGLContext currentContext]) {
        [EAGLContext setCurrentContext:context];
    }

    if (!defaultFramebuffer) {
        // Perform on the main thread to ensure safe memory allocation for the
        // shared buffer.  Block until the operation is complete to prevent
        // simultaneous access to the OpenGL context
        [self performSelectorOnMainThread:@selector(createFramebuffer) withObject:self waitUntilDone:YES];
    }

    glBindFramebuffer(GL_FRAMEBUFFER, defaultFramebuffer);
}


- (BOOL)presentFramebuffer
{
    // setFramebuffer must have been called before presentFramebuffer, therefore
    // we know the context is valid and has been set for this (render) thread

    // Bind the colour render buffer and present it
    glBindRenderbuffer(GL_RENDERBUFFER, colorRenderbuffer);

    return [context presentRenderbuffer:GL_RENDERBUFFER];
}


-(void) setDetectTarget:(NSString *) target {
    _detectedTarget = target;
}

@end
