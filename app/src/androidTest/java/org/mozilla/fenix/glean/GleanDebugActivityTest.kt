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
    fun `pings are sent using sendPing`() {
        //val server = getMockWebServer()
        var server = MockWebServer()

        val context = ApplicationProvider.getApplicationContext<Context>()
//        resetGlean(context, Glean.configuration.copy(
//            serverEndpoint = "http://" + server.hostName + ":" + server.port
//        ))

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
//        Glean.configuration = Glean.configuration.copy(
//            serverEndpoint = "http://" + server.hostName + ":" + server.port
//        )

        triggerWorkManager(context)
//        val request = server.takeRequest(10L, TimeUnit.SECONDS)

        assertTrue(
            request.requestUrl.encodedPath().startsWith("/submit/mozilla-telemetry-glean-test/metrics")
        )

//        server.shutdown()
    }

}
