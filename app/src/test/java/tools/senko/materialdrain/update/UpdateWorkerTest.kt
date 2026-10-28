package tools.senko.materialdrain.update

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The daily check in the background: planned while its setting is on and not otherwise, and a notification once per
 * version, none for the version the user skipped.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun planned(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("update-check").get().filter { !it.state.isFinished }

    @Test
    fun plannedOnlyWhileTurnedOn() {
        UpdateWorker.schedule(context, enabled = true)
        assertEquals(1, planned().size)
        // Turned on again, it stays the one planned
        UpdateWorker.schedule(context, enabled = true)
        assertEquals(1, planned().size)
        UpdateWorker.schedule(context, enabled = false)
        assertTrue(planned().isEmpty())
    }

    @Test
    fun oneNotificationPerVersionNoneForASkippedOne() {
        assertTrue(UpdateWorker.shouldNotify(1001, skipped = 0, notified = 0))
        assertFalse("Told already", UpdateWorker.shouldNotify(1001, skipped = 0, notified = 1001))
        assertFalse("Skipped", UpdateWorker.shouldNotify(1001, skipped = 1001, notified = 0))
        assertTrue("A newer one than the skipped", UpdateWorker.shouldNotify(1002, skipped = 1001, notified = 1001))
    }
}
