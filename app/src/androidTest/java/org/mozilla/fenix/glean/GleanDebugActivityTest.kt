package org.mozilla.fenix.glean

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//package mozilla.telemetry.glean.debug

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

import mozilla.telemetry.glean.private.BooleanMetricType
import mozilla.telemetry.glean.private.Lifetime
//import mozilla.telemetry.glean.resetGlean
import mozilla.components.service.glean.resetGlean
import mozilla.telemetry.glean.triggerWorkManager
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

/**
 * This is a helper class to facilitate testing of ping tagging
 */
private class TestPingTagClient(
    private val responseUrl: String = Configuration.DEFAULT_TELEMETRY_ENDPOINT,
    private val debugHeaderValue: String? = null
) : PingUploader {
    override fun upload(url: String, data: ByteArray, headers: HeadersList): UploadResult {
        assertTrue("URL must be redirected for tagged pings",
            url.startsWith(responseUrl))
        assertEquals("Debug headers must match what the ping tag was set to",
            debugHeaderValue, headers.find { it.first == "X-Debug-ID" }!!.second)

        return HttpResponse(200)
    }
}

@RunWith(AndroidJUnit4::class)
class GleanDebugActivityTest {

    private val testPackageName = "mozilla.telemetry.glean.test"

    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())

    @Before
    fun setup() {
        // This makes sure we have a "launch" intent in our package, otherwise
        // it will fail looking for it in `GleanDebugActivityTest`.
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN)
        launchIntent.setPackage(testPackageName)
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = ActivityInfo()
        resolveInfo.activityInfo.packageName = testPackageName
        resolveInfo.activityInfo.name = "LauncherActivity"
        @Suppress("DEPRECATION")
        shadowOf(pm).addResolveInfoForIntent(launchIntent, resolveInfo)
    }

    @Test
    fun `the default configuration is not changed if no extras are provided`() {
        val originalConfig = Configuration()
        Glean.configuration = originalConfig

        // Build the intent that will call our debug activity, with no extra.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        assertNull(intent.extras)

        // Start the activity through our intent.
        launch<GleanDebugActivity>(intent)

        // Verify that the original configuration and the one after init took place
        // are the same.
        assertEquals(originalConfig, Glean.configuration)
    }

    @Test
    fun `command line extra arguments are correctly parsed`() {
        // Make sure to set a baseline configuration to check against.
        val originalConfig = Configuration()
        Glean.configuration = originalConfig
        assertFalse(originalConfig.logPings)

        // Set the extra values and start the intent.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        intent.putExtra(GleanDebugActivity.LOG_PINGS_EXTRA_KEY, true)
        launch<GleanDebugActivity>(intent)

        // Check that the configuration option was correctly flipped.
        assertTrue(Glean.configuration.logPings)
    }

    @Test
    fun `the main activity is correctly started`() {
        // Build the intent that will call our debug activity, with no extra.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        // Add at least an option, otherwise the activity will be removed.
        intent.putExtra(GleanDebugActivity.LOG_PINGS_EXTRA_KEY, true)
        // Start the activity through our intent.
        val scenario = launch<GleanDebugActivity>(intent)

        // Check that our main activity was launched.
        scenario.onActivity { activity ->
            assertEquals(testPackageName,
                shadowOf(activity).peekNextStartedActivityForResult().intent.`package`!!)
        }
    }

    @Test
    fun `pings are sent using sendPing`() {
        //val server = getMockWebServer()
        var server = MockWebServer()

        val context = ApplicationProvider.getApplicationContext<Context>()
        resetGlean(context, Glean.configuration.copy(
            serverEndpoint = "http://" + server.hostName + ":" + server.port
        ))

        // Put some metric data in the store, otherwise we won't get a ping out
        // Define a 'booleanMetric' boolean metric, which will be stored in "store1"
        val booleanMetric = BooleanMetricType(
            disabled = false,
            category = "telemetry",
            lifetime = Lifetime.Application,
            name = "boolean_metric",
            sendInPings = listOf("metrics")
        )

        booleanMetric.set(true)
        assertTrue(booleanMetric.testHasValue())

        // Set the extra values and start the intent.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        intent.putExtra(GleanDebugActivity.SEND_PING_EXTRA_KEY, "metrics")
        launch<GleanDebugActivity>(intent)

        // Since we reset the serverEndpoint back to the default for untagged pings, we need to
        // override it here so that the local server we created to intercept the pings will
        // be the one that the ping is sent to.
        Glean.configuration = Glean.configuration.copy(
            serverEndpoint = "http://" + server.hostName + ":" + server.port
        )

        triggerWorkManager(context)
        val request = server.takeRequest(10L, TimeUnit.SECONDS)

        assertTrue(
            request.requestUrl.encodedPath().startsWith("/submit/mozilla-telemetry-glean-test/metrics")
        )

        server.shutdown()
    }

    @Test
    fun `tagPings filters ID's that don't match the pattern`() {
        val server = getMockWebServer()

        val context = ApplicationProvider.getApplicationContext<Context>()
        resetGlean(context, Glean.configuration.copy(
            serverEndpoint = "http://" + server.hostName + ":" + server.port
        ))

        // Put some metric data in the store, otherwise we won't get a ping out
        // Define a 'booleanMetric' boolean metric, which will be stored in "store1"
        val booleanMetric = BooleanMetricType(
            disabled = false,
            category = "telemetry",
            lifetime = Lifetime.Application,
            name = "boolean_metric",
            sendInPings = listOf("metrics")
        )

        booleanMetric.set(true)
        assertTrue(booleanMetric.testHasValue())

        // Set the extra values and start the intent.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        intent.putExtra(GleanDebugActivity.SEND_PING_EXTRA_KEY, "metrics")
        intent.putExtra(GleanDebugActivity.TAG_DEBUG_VIEW_EXTRA_KEY, "inv@lid_id")
        launch<GleanDebugActivity>(intent)

        // Since a bad tag ID results in resetting the endpoint to the default, verify that
        // has happened.
        assertEquals("Server endpoint must be reset if tag didn't pass regex",
            "http://" + server.hostName + ":" + server.port, Glean.configuration.serverEndpoint)

        triggerWorkManager(context)
        val request = server.takeRequest(10L, TimeUnit.SECONDS)

        assertTrue(
            "Request path must be correct",
            request.requestUrl.encodedPath().startsWith("/submit/mozilla-telemetry-glean-test/metrics")
        )

        assertNull(
            "Headers must not contain X-Debug-ID if passed a non matching pattern",
            request.headers.get("X-Debug-ID")
        )

        server.shutdown()
    }

    @Test
    fun `pings are correctly tagged using tagPings`() {
        val pingTag = "test-debug-ID"

        // Use the test client in the Glean configuration
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetGlean(context, Glean.configuration.copy(
            httpClient = TestPingTagClient(debugHeaderValue = pingTag)
        ))

        // Put some metric data in the store, otherwise we won't get a ping out
        // Define a 'booleanMetric' boolean metric, which will be stored in "store1"
        val booleanMetric = BooleanMetricType(
            disabled = false,
            category = "telemetry",
            lifetime = Lifetime.Application,
            name = "boolean_metric",
            sendInPings = listOf("metrics")
        )

        booleanMetric.set(true)
        assertTrue(booleanMetric.testHasValue())

        // Set the extra values and start the intent.
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(),
            GleanDebugActivity::class.java)
        intent.putExtra(GleanDebugActivity.SEND_PING_EXTRA_KEY, "metrics")
        intent.putExtra(GleanDebugActivity.TAG_DEBUG_VIEW_EXTRA_KEY, pingTag)
        launch<GleanDebugActivity>(intent)

        // This will trigger the call to `fetch()` in the TestPingTagClient which is where the
        // test assertions will occur
        triggerWorkManager(context)
    }
}
