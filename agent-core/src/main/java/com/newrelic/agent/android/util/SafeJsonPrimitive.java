/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.google.gson.JsonPrimitive;

/**
 * The constructors of JsonPrimitive will allow null value input, which will NPE in
 * isPrimitiveOrString: 'target instanceof String' evals to true, and next statement is reference
 * to the (null) instance method .getClass();
 */
public class SafeJsonPrimitive {

    public static final String NULL_STRING = "null";
    public static final Number NULL_NUMBER = Float.NaN;
    public static final Boolean NULL_BOOL = Boolean.FALSE;
    public static final char NULL_CHAR = ' ';

    public static String checkNull(String string) {
        return string == null ? NULL_STRING : string;
    }

    public static Boolean checkNull(Boolean bool) {
        return bool == null ? NULL_BOOL : bool;
    }

    public static Number checkNull(Number number) {
        return number == null ? NULL_NUMBER : number;
    }

    public static Character checkNull(Character c) {
        return c == null ? NULL_CHAR : c;
    }

    public static JsonPrimitive factory(Boolean bool) {
        return new JsonPrimitive(checkNull(bool));
    }

    public static JsonPrimitive factory(Number number) {
        return new JsonPrimitive(checkNull(number));
    }

    public static JsonPrimitive factory(String string) {
        return new JsonPrimitive(checkNull(string));
    }

    public static JsonPrimitive factory(Character character) {
        return new JsonPrimitive(checkNull(character));
    }

    public static JsonPrimitive factory(Double number) {
        // Large Long values stored in attributes as Double do not transfer
        // well to Dirac, so covert the Json type to Long and avoid the conversion
        if ((number.floatValue() > Integer.MAX_VALUE) && (number.longValue() == number)) {
            return new JsonPrimitive(Long.valueOf(number.longValue()));
        }

        return new JsonPrimitive(checkNull(number));
    }
}
