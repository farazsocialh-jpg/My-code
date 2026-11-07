package com.sportage.obd

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import com.sportage.obd.map.NavigationLauncher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NavigationLauncherTest {
    @Test fun implicitGeoIntentAllowsSystemDefaultsAndJustOnceOrAlways() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(NavigationLauncher.open(context, 25.1712, 51.567))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo:25.1712,51.567?z=16&q=25.1712,51.567(My%20Location)", started.data.toString())
        assertNull("Must not restrict selection to a particular vendor", started.`package`)
        assertNull("Must let Android resolve compatible installed apps", started.component)
        assertNotEquals("Forced chooser must not suppress Android's Always option", Intent.ACTION_CHOOSER, started.action)
    }

    @Test fun missingNavigationAppsDoesNotFallBackToBrowserOrStore() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("No geo handler")
            }
        }
        assertFalse(NavigationLauncher.open(context, 25.1712, 51.567))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test fun invalidLocationDoesNotLaunch() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(NavigationLauncher.open(context, Double.NaN, 51.567))
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
