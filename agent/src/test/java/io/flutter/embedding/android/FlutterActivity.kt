/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.flutter.embedding.android

import android.app.Activity

/** Test-only stand-in so an app activity can extend the class the detector looks for by name. */
open class FlutterActivity : Activity()
