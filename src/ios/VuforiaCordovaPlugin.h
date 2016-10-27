#import <Cordova/CDV.h>
#import <UIKit/UIKit.h>

@interface VuforiaCordovaPlugin : CDVPlugin

@property (nonatomic, retain) NSString *callbackID;

- (void) isDetecting:(CDVInvokedUrlCommand *)command;
- (void) setLang:(CDVInvokedUrlCommand *)command;
- (void) autoPlay:(CDVInvokedUrlCommand *)command;
- (void) rotateScreen:(CDVInvokedUrlCommand *)command;

- (void) updateDetectedTarget:(BOOL)foundTarget target:(NSString*)targetName;

@end
