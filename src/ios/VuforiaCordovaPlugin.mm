#import "VuforiaCordovaPlugin.h"
#import "VuforiaViewController.h"

@interface VuforiaCordovaPlugin()
{
    NSString *lang;
    NSString *detectedTarget;
    BOOL playOnDetection, callbackSet, called;
    VuforiaViewController *vuforiaViewController;
}

@property CDVInvokedUrlCommand *command;

@end

@implementation VuforiaCordovaPlugin

- (void) isDetecting:(CDVInvokedUrlCommand *)command {
    NSLog(@"isDetecting called");
    self.callbackID = command.callbackId;
    callbackSet = true;

    CDVPluginResult* plugin_result = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [plugin_result setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:plugin_result callbackId:self.callbackID];
}

- (void) setLang:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSLog(@"setLang called");
        CDVPluginResult* pluginResult = nil;
        NSString* _lang = [command.arguments objectAtIndex:0];

        if(_lang != nil && [_lang length] > 0) {
            lang = _lang;
            NSString* msg = [NSString stringWithFormat:@"Language was set to: %@", _lang];
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:msg];
        } else {
            NSString* msg = @"Error: missing language.";
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:msg];
        }

        [self.commandDelegate sendPluginResult:pluginResult callbackId: command.callbackId];

        [self saveLocally];
    }];
}

- (void) autoPlay:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSLog(@"autoPlay called");
        CDVPluginResult* pluginResult = nil;
        BOOL _autoPlay = [[command argumentAtIndex:0] boolValue];

        playOnDetection = _autoPlay;
        NSString* msg = [NSString stringWithFormat:@"Auto-play was set to: %s", _autoPlay ? "true" : "false"];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:msg];

        [self.commandDelegate sendPluginResult:pluginResult callbackId: command.callbackId];

        [self saveLocally];
    }];
}

-(void) pluginInitialize {
    lang = @"nl";
    detectedTarget = @"";
    playOnDetection = true;
    callbackSet = false;
    called = false;

    [self saveLocally];

    // make webview transparent
    for (UIView *subview in [self.webView subviews]) {
        [subview setOpaque:NO];
        [subview setBackgroundColor:[UIColor clearColor]];
    }
    [self.webView setBackgroundColor:[UIColor clearColor]];
    [self.webView setOpaque: NO];

    // add view controller and vuforia view to superview
    vuforiaViewController = [[VuforiaViewController alloc] init];
    [vuforiaViewController addCordovaPlugin:self];
    [self.viewController addChildViewController:vuforiaViewController];
    vuforiaViewController.view.frame = [[UIScreen mainScreen] bounds];
    [self.webView.superview addSubview:vuforiaViewController.view];
    [self.webView.superview bringSubviewToFront:self.webView];
    [vuforiaViewController didMoveToParentViewController:self.viewController];
}

-(void) sendDetectionUpdate:(BOOL)state target:(NSString*)targetName {
    int index = 0;

    if(targetName && [targetName caseInsensitiveCompare:@"tia"] == NSOrderedSame) {
        index = 0;
    } else if(targetName && [targetName caseInsensitiveCompare:@"kids"] == NSOrderedSame) {
        index = 1;
    } else if(targetName && [targetName caseInsensitiveCompare:@"tombstone"] == NSOrderedSame) {
        index = 2;
    }

    [self.commandDelegate runInBackground:^{
        CDVPluginResult* plugin_result = nil;
        NSString* jsonObj = [NSString stringWithFormat:@"{\"state\": %s, \"target\": \"%@\", \"index\": \"%d\"}", state ? "true" : "false", targetName, index];
        if (state) {
            plugin_result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonObj];
        } else {
            plugin_result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:jsonObj];
        }
        [plugin_result setKeepCallbackAsBool:YES];

        [self.commandDelegate sendPluginResult:plugin_result callbackId:self.callbackID];
    }];
}

- (void) updateDetectedTarget:(BOOL)foundTarget target:(NSString*)targetName {
    if(callbackSet) {
        if (foundTarget) {
            // if the target changed update it and notify
            if (targetName && [targetName caseInsensitiveCompare:detectedTarget] != NSOrderedSame) {
                detectedTarget = targetName;
                [self sendDetectionUpdate:true target:detectedTarget];
            }
            called = false;
        } else {
            // if no target is detected after a detection, update it and notify
            if(!called) {
                detectedTarget = @"";
                [self sendDetectionUpdate:false target:detectedTarget];
                called = true;
            }
        }
    }
}

- (void) saveLocally {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:lang forKey:@"lang"];
    [defaults setBool:playOnDetection forKey:@"playOnDetection"];
    if([defaults synchronize]) {
        NSLog(@"Data saved locally: lang -> %@, playOnDetection -> %@", lang, playOnDetection ? @"true" : @"false");
    } else {
        NSLog(@"Error saving data locally: lang -> %@, playOnDetection -> %@", lang, playOnDetection ? @"true" : @"false");
    }
}

@end
