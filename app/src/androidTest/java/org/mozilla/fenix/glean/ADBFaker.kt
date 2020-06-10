package org.mozilla.fenix.glean

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//package mozilla.telemetry.glean.debug

import android.content.ActivityNotFoundException
import mozilla.components.concept.fetch.Client


import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
//import mozilla.telemetry.glean.Glean
import mozilla.components.service.glean.Glean
import mozilla.telemetry.glean.config.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mozilla.telemetry.glean.debug.GleanDebugActivity

import mozilla.telemetry.glean.private.BooleanMetricType
import mozilla.telemetry.glean.private.Lifetime
//import mozilla.telemetry.glean.resetGlean
//import mozilla.telemetry.glean.triggerWorkManager
//import mozilla.telemetry.glean.getMockWebServer
import okhttp3.mockwebserver.MockWebServer

import mozilla.telemetry.glean.net.HeadersList
import mozilla.telemetry.glean.net.PingUploader
import mozilla.telemetry.glean.net.UploadResult
import mozilla.telemetry.glean.net.HttpResponse
import mozilla.telemetry.glean.testing.GleanTestRule
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ADBFaker {

    @Test
    fun runFakeAdb() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        /*val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            `package` = "org.mozilla.fenix.debug"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }*/
        val intent = Intent(context, GleanDebugActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        intent.putExtra(GleanDebugActivity.LOG_PINGS_EXTRA_KEY, true)
        // adding this tag, stuff will showup in the debug view website: https://debug-ping-preview.firebaseapp.com/
        // Alessio will ping when this can be replaced (EOM TBD)
//        intent.putExtra(GleanDebugActivity.TAG_DEBUG_VIEW_EXTRA_KEY, "adb-faker-test")

        try {
            context.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            intent.setPackage(null)
            context.startActivity(intent)
        }

        // add espresso steps here with desired activity

    }
}

