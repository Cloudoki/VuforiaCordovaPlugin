package com.cloudoki.vuforiaplugin.utils;

import android.util.Log;

import io.cordova.hellocordova.BuildConfig;

public class Logger {

    public static final boolean DEBUG = BuildConfig.DEBUG;

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

    public static void e(String msg) {
        if (DEBUG) Log.e(LOG_TAG, msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG) Log.e(tag, msg);
    }

    public static void e(String msg, Throwable throwable) {
        if (DEBUG) Log.e(LOG_TAG, msg, throwable);
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
}
