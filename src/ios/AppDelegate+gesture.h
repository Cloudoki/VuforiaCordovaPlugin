//
//  AppDelegate+gesture.h
//  VuforiaTest
//
//  Created by DNVA on 18/10/16.
//
//

#import "AppDelegate.h"

@interface AppDelegate (notification)


- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer;

@end
