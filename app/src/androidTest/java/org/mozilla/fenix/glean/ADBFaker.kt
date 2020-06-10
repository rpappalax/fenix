package org.mozilla.fenix.glean

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import android.content.ActivityNotFoundException
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mozilla.telemetry.glean.debug.GleanDebugActivity
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mozilla.fenix.helpers.AndroidAssetDispatcher
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.helpers.TestAssetHelper
import org.mozilla.fenix.ui.robots.navigationToolbar

@RunWith(AndroidJUnit4::class)
class ADBFaker {
    private lateinit var mockWebServer: MockWebServer

    @get:Rule
    val activityTestRule = HomeActivityTestRule()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply {
            setDispatcher(AndroidAssetDispatcher())
            start()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }


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
        intent.putExtra(GleanDebugActivity.TAG_DEBUG_VIEW_EXTRA_KEY, "adb-faker-test")

        try {
            context.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            intent.setPackage(null)
            context.startActivity(intent)
        }

        // add espresso steps here with desired activity
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val nextWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
            verifyPageContent(defaultWebPage.content)
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(nextWebPage.url) {
            verifyPageContent(nextWebPage.content)
        }

    }
}

