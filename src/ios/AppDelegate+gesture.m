//
//  AppDelegate+gesture.m
//  VuforiaTest
//
//  Created by DNVA on 18/10/16.
//
//

#import "AppDelegate.h"

@implementation AppDelegate (gestures)

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer{
    return YES;
}

@end
