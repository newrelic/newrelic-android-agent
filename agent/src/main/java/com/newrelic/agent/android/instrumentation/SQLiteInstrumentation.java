/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;

import com.newrelic.agent.android.tracing.TraceMachine;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("unused")
public class SQLiteInstrumentation {
    private static final ArrayList<String> categoryParams = new ArrayList<String>(Arrays.asList("category", MetricCategory.class.getName(), "DATABASE"));

    SQLiteInstrumentation() {}

    @ReplaceCallSite
    public static Cursor query(SQLiteDatabase database, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        TraceMachine.enterMethod("SQLiteDatabase#query", categoryParams);
        final Cursor cursor = database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    @TargetApi(16)
    public static Cursor query(SQLiteDatabase database, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        TraceMachine.enterMethod("SQLiteDatabase#query", categoryParams);
        final Cursor cursor = database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, cancellationSignal);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static Cursor query(SQLiteDatabase database, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        TraceMachine.enterMethod("SQLiteDatabase#query", categoryParams);
        final Cursor cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static Cursor query(SQLiteDatabase database, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        TraceMachine.enterMethod("SQLiteDatabase#query", categoryParams);
        final Cursor cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static Cursor queryWithFactory(SQLiteDatabase database, SQLiteDatabase.CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        TraceMachine.enterMethod("SQLiteDatabase#queryWithFactory", categoryParams);
        final Cursor cursor = database.queryWithFactory(cursorFactory, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    @TargetApi(16)
    public static Cursor queryWithFactory(SQLiteDatabase database, SQLiteDatabase.CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        TraceMachine.enterMethod("SQLiteDatabase#queryWithFactory", categoryParams);
        final Cursor cursor = database.queryWithFactory(cursorFactory, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, cancellationSignal);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static Cursor rawQuery(SQLiteDatabase database, String sql, String[] selectionArgs) {
        TraceMachine.enterMethod("SQLiteDatabase#rawQuery", categoryParams);
        final Cursor cursor = database.rawQuery(sql, selectionArgs);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    @TargetApi(16)
    public static Cursor rawQuery(SQLiteDatabase database, String sql, String[] selectionArgs, CancellationSignal cancellationSignal) {
        TraceMachine.enterMethod("SQLiteDatabase#rawQuery", categoryParams);
        final Cursor cursor = database.rawQuery(sql, selectionArgs, cancellationSignal);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static Cursor rawQueryWithFactory(SQLiteDatabase database, SQLiteDatabase.CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable) {
        TraceMachine.enterMethod("SQLiteDatabase#rawQueryWithFactory", categoryParams);
        final Cursor cursor = database.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    @TargetApi(16)
    public static Cursor rawQueryWithFactory(SQLiteDatabase database, SQLiteDatabase.CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable, CancellationSignal cancellationSignal) {
        TraceMachine.enterMethod("SQLiteDatabase#rawQueryWithFactory", categoryParams);
        final Cursor cursor = database.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable, cancellationSignal);
        TraceMachine.exitMethod();

        return cursor;
    }

    @ReplaceCallSite
    public static long insert(SQLiteDatabase database, String table, String nullColumnHack, ContentValues values) {
        TraceMachine.enterMethod("SQLiteDatabase#insert", categoryParams);
        final long result = database.insert(table, nullColumnHack, values);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static long insertOrThrow(SQLiteDatabase database, String table, String nullColumnHack, ContentValues values) throws SQLException  {
        TraceMachine.enterMethod("SQLiteDatabase#insertOrThrow", categoryParams);
        final long result = database.insertOrThrow(table, nullColumnHack, values);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static long insertWithOnConflict(SQLiteDatabase database, String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
        TraceMachine.enterMethod("SQLiteDatabase#insertWithOnConflict", categoryParams);
        final long result = database.insertWithOnConflict(table, nullColumnHack, initialValues, conflictAlgorithm);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static long replace(SQLiteDatabase database, String table, String nullColumnHack, ContentValues initialValues) {
        TraceMachine.enterMethod("SQLiteDatabase#replace", categoryParams);
        final long result = database.replace(table, nullColumnHack, initialValues);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static long replaceOrThrow(SQLiteDatabase database, String table, String nullColumnHack, ContentValues initialValues) throws SQLException {
        TraceMachine.enterMethod("SQLiteDatabase#replaceOrThrow", categoryParams);
        final long result = database.replaceOrThrow(table, nullColumnHack, initialValues);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static int delete(SQLiteDatabase database, String table, String whereClause, String[] whereArgs) {
        TraceMachine.enterMethod("SQLiteDatabase#delete", categoryParams);
        final int result = database.delete(table, whereClause, whereArgs);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static int update(SQLiteDatabase database, String table, ContentValues values, String whereClause, String[] whereArgs) {
        TraceMachine.enterMethod("SQLiteDatabase#update", categoryParams);
        final int result = database.update(table, values, whereClause, whereArgs);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static int updateWithOnConflict(SQLiteDatabase database, String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
        TraceMachine.enterMethod("SQLiteDatabase#updateWithOnConflict", categoryParams);
        final int result = database.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm);
        TraceMachine.exitMethod();

        return result;
    }

    @ReplaceCallSite
    public static void execSQL(SQLiteDatabase database, String sql) throws SQLException {
        TraceMachine.enterMethod("SQLiteDatabase#execSQL", categoryParams);
        database.execSQL(sql);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite
    public static void execSQL(SQLiteDatabase database, String sql, Object[] bindArgs) throws SQLException {
        TraceMachine.enterMethod("SQLiteDatabase#execSQL", categoryParams);
        database.execSQL(sql, bindArgs);
        TraceMachine.exitMethod();
    }
}
