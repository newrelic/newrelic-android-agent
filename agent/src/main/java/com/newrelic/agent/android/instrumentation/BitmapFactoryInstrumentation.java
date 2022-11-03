/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.TypedValue;

import com.newrelic.agent.android.tracing.TraceMachine;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("unused")
public class BitmapFactoryInstrumentation {
    private static final ArrayList<String> categoryParams = new ArrayList<String>(Arrays.asList("category", MetricCategory.class.getName(), "IMAGE"));

    private BitmapFactoryInstrumentation() {}

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeFile(String pathName, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeFile", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeFile(pathName, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeFile(String pathName) {
        TraceMachine.enterMethod("BitmapFactory#decodeFile", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeFile(pathName);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeResourceStream(Resources res, TypedValue value, InputStream is, Rect pad, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeResourceStream", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeResourceStream(res, value, is, pad, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeResource(Resources res, int id, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeResource", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeResource(res, id, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeResource(Resources res, int id) {

        TraceMachine.enterMethod("BitmapFactory#decodeResource", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeResource(res, id);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeByteArray(byte[] data, int offset, int length, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeByteArray", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeByteArray(data, offset, length, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        TraceMachine.enterMethod("BitmapFactory#decodeByteArray", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeByteArray(data, offset, length);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeStream(InputStream is, Rect outPadding, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeStream", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeStream(is, outPadding, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeStream(InputStream is) {
        TraceMachine.enterMethod("BitmapFactory#decodeStream", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeStream(is);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeFileDescriptor(FileDescriptor fd, Rect outPadding, BitmapFactory.Options opts) {
        TraceMachine.enterMethod("BitmapFactory#decodeFileDescriptor", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd, outPadding, opts);
        TraceMachine.exitMethod();

        return bitmap;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.graphics.BitmapFactory")
    public static Bitmap decodeFileDescriptor(FileDescriptor fd) {
        TraceMachine.enterMethod("BitmapFactory#decodeFileDescriptor", categoryParams);
        final Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd);
        TraceMachine.exitMethod();

        return bitmap;
    }
}
