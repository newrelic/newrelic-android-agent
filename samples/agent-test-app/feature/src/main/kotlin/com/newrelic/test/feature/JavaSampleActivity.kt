/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.test.feature

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.newrelic.feature.R

/** A simple activity displaying text written in Java.  */
class JavaSampleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_java)
    }
}