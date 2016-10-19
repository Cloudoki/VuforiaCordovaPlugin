/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

#import <UIKit/UIKit.h>
#import "VuforiaEAGLView.h"
#import "VuforiaAppSession.h"
#import <Vuforia/DataSet.h>

#import "VuforiaCordovaPlugin.h"

@interface VuforiaViewController : UIViewController <VuforiaAppControl> {

    Vuforia::DataSet*  dataSetCurrent;
    Vuforia::DataSet*  dataSetTarmac;
    Vuforia::DataSet*  dataSetStonesAndChips;
    Vuforia::DataSet*  dataSetVideo;
    BOOL fullScreenPlayerPlaying;

    BOOL switchToTarmac;
    BOOL switchToStonesAndChips;

    // menu options
    BOOL extendedTrackingEnabled;
    BOOL continuousAutofocusEnabled;
    BOOL flashEnabled;
    BOOL playFullscreenEnabled;
    BOOL frontCameraEnabled;
}

- (void)rootViewControllerPresentViewController:(UIViewController*)viewController inContext:(BOOL)currentContext;
- (void)rootViewControllerDismissPresentedViewController;

- (void) addCordovaPlugin:(VuforiaCordovaPlugin *) cordovaPlugin;

@property (nonatomic, strong) VuforiaEAGLView* eaglView;
@property (nonatomic, strong) UITapGestureRecognizer * tapGestureRecognizer;
@property (nonatomic, strong) VuforiaAppSession * vapp;

@end
