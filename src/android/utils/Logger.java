package com.cloudoki.vuforiacordovaplugin.utils;

import android.util.Log;

// in debug log all but in prod only errors
public class Logger {

    public static final boolean DEBUG = true;

    public static final String LOG_TAG = "VUFORIA_APP";

    public static void i(String msg) {
        if (DEBUG) Log.i(LOG_TAG, msg);
    }

    public static void i(String tag, String msg) {
        if (DEBUG) Log.i(tag, msg);
    }

    public static void i(String msg, Throwable throwable) {
        if (DEBUG) Log.i(LOG_TAG, msg, throwable);
    }

    public static void d(String msg) {
        if (DEBUG) Log.d(LOG_TAG, msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG) Log.d(tag, msg);
    }

    public static void d(String msg, Throwable throwable) {
        if (DEBUG) Log.d(LOG_TAG, msg, throwable);
    }

    public static void e(String msg) {
        Log.e(LOG_TAG, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void e(String msg, Throwable throwable) {
        Log.e(LOG_TAG, msg, throwable);
    }
}
