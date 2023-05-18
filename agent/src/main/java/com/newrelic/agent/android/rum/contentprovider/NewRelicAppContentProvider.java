/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum.contentprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;

import com.newrelic.agent.android.rum.AppApplicationLifeCycle;
import com.newrelic.agent.android.rum.AppTracer;

public class NewRelicAppContentProvider extends ContentProvider {

    AppApplicationLifeCycle appApplicationLifeCycle = new AppApplicationLifeCycle();

    @Override
    public boolean onCreate() {
        AppTracer.getInstance().setContentProviderStartedTime(SystemClock.uptimeMillis());

        Context context = getContext();
        if (context == null) {
            return false;
        } else {
            appApplicationLifeCycle.onColdStartInitiated(context);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] strings, String s, String[] strings1, String s1) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }
}
