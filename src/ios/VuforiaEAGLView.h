/*===============================================================================
 Copyright (c) 2016 PTC Inc. All Rights Reserved.

 Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

 Vuforia is a trademark of PTC Inc., registered in the United States and other
 countries.
 ===============================================================================*/

#import <UIKit/UIKit.h>

#import <Vuforia/UIGLViewProtocol.h>

#import "Texture.h"
#import "VuforiaAppSession.h"
#import "SampleApplication3DModel.h"
#import "VuforiaGLResourceHandler.h"
#import "AppRenderer.h"
#import "VideoPlayerHelper.h"

#define kNumImageAugmentationTextures 1
static const int kNumVideoAugmentationTextures = 5;
static const int kNumVideoTargets = 2;

// EAGLView is a subclass of UIView and conforms to the informal protocol
// UIGLViewProtocol
@interface VuforiaEAGLView : UIView <UIGLViewProtocol, VuforiaGLResourceHandler, AppRendererControl> {
@private
    // Instantiate one VideoPlayerHelper per target
    VideoPlayerHelper* videoPlayerHelper[kNumVideoTargets];
    float videoPlaybackTime[kNumVideoTargets];

    // Timer to pause on-texture video playback after tracking has been lost.
    // Note: written/read on two threads, but never concurrently
    NSTimer* trackingLostTimer;

    // Coordinates of user touch
    float touchLocation_X;
    float touchLocation_Y;

    // indicates how the video will be played
    BOOL playVideoFullScreen;

    // Lock to synchronise data that is (potentially) accessed concurrently
    NSLock* dataLock;

    // OpenGL ES context
    EAGLContext *context;

    // The OpenGL ES names for the framebuffer and renderbuffers used to render
    // to this view
    GLuint defaultFramebuffer;
    GLuint colorRenderbuffer;
    GLuint depthRenderbuffer;

    // Shader handles
    GLuint shaderProgramID;
    GLint vertexHandle;
    GLint normalHandle;
    GLint textureCoordHandle;
    GLint mvpMatrixHandle;
    GLint texSampler2DHandle;

    // Texture used when rendering augmentation
    Texture* imageAugmentationTexture[kNumImageAugmentationTextures];
    Texture* videoAugmentationTexture[kNumVideoAugmentationTextures];


    BOOL offTargetTrackingEnabled;
    SampleApplication3DModel * buildingModel;

    AppRenderer * appRenderer;
}

@property (nonatomic, assign) VuforiaAppSession * vapp;

- (id)initWithFrame:(CGRect)frame appSession:(VuforiaAppSession *) app controller:(VuforiaViewController *)viewController;

- (void) willPlayVideoFullScreen:(BOOL) fullScreen;

- (void) prepare;
- (void) dismiss;

- (bool) handleTouchPoint:(CGPoint) touchPoint;

- (void) preparePlayers;
- (void) dismissPlayers;

- (void)finishOpenGLESCommands;
- (void)freeOpenGLESResources;

- (void) setOffTargetTrackingMode:(BOOL) enabled;

- (void) setDetectTarget:(NSString *) target;
@end
