/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressLint("NewApi")
public abstract class SharedPrefsStore {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final Charset ENCODING = Charset.forName("ISO-8859-1");

    protected final SharedPreferences sharedPrefs;
    protected final String storeFilename;

    public SharedPrefsStore(Context context, String storeFilename) {
        this.sharedPrefs = context.getSharedPreferences(storeFilename, Context.MODE_PRIVATE);
        this.storeFilename = storeFilename;
    }

    public String getStoreFilename() {
        return storeFilename;
    }

    public boolean store(String uuid, byte[] bytes) {
        try {
            final SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(uuid, decodeBytesToString(bytes));
            return applyOrCommitEditor(editor);
        } catch (Exception e) {
            log.error("SharedPrefsStore.store(String, byte[]): ", e);
        }

        return false;
    }

    public boolean store(String uuid, Set<String> stringSet) {
        try {
            final SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putStringSet(uuid, stringSet);
            return applyOrCommitEditor(editor);
        } catch (Exception e) {
            log.error("SharedPrefsStore.store(String, Set<String>): ", e);
        }

        return false;
    }

    public boolean store(String uuid, String string) {
        try {
            final SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(uuid, string);
            return applyOrCommitEditor(editor);
        } catch (Exception e) {
            log.error("SharedPrefsStore.store(String, String): ", e);
        }

        return false;
    }

    public List<?> fetchAll() {
        final ArrayList<Object> objectList = new ArrayList<Object>();

        try {
            synchronized (this) {
                Map<String, ?> objectStrings = sharedPrefs.getAll();
                objectList.addAll(objectStrings.values());
            }
        } catch (Exception e) {
            log.error("SharedPrefsStore.fetchAll(): ", e);
        }

        return objectList;
    }

    public int count() {
        try {
            synchronized (sharedPrefs) {
                return sharedPrefs.getAll().size();
            }
        } catch (Exception e) {
            log.error("SharedPrefsStore.count(): ", e);

        }

        return 0;
    }

    public void clear() {
        try {
            synchronized (this) {
                final SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.clear();
                applyOrCommitEditor(editor);
            }
        } catch (Exception e) {
            log.error("SharedPrefsStore.clear(): ", e);
        }
    }

    public void delete(String uuid) {
        try {
            synchronized (this) {
                final SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.remove(uuid);
                applyOrCommitEditor(editor);
            }
        } catch (Exception e) {
            log.error("SharedPrefsStore.delete(): ", e);
        }
    }

    protected String encodeBytes(byte[] bytes) {
        try {
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            log.error("SharedPrefsStore.encodeBytes(byte[]): ", e);
        }

        return null;
    }

    protected byte[] decodeStringToBytes(String encodedString) {
        try {
            return Base64.decode(encodedString, Base64.DEFAULT);
        } catch (Exception e) {
            log.error("SharedPrefsStore.decodeStringToBytes(String): ", e);
        }

        return null;
    }

    protected String decodeBytesToString(byte[] decodedString) {
        try {
            return new String(decodedString, ENCODING);
        } catch (Exception e) {
            log.error("SharedPrefsStore.decodeBytesToString(byte[]): ", e);
        }

        return null;
    }

    /**
     * Finalize the shared prefs editor by selectively calling .commit() or .apply()
     * .apply() is threaded. but only supported after Android SDK 9
     * @param editor
     * @return true if SDK > Gingerbread, result of .commit() otherwise
     */
    @SuppressLint("CommitPrefEdits")
    protected boolean applyOrCommitEditor(SharedPreferences.Editor editor) {
        boolean result = true;
        try {
            editor.apply(); // call non-blocking commit
        } catch (Exception e) {
            log.error("SharedPrefsStore.applyOrCommitEditor(SharedPreferences.Editor): ", e);

        }
        return result;
    }

}
