package com.pawtillery.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdaterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** A phone running build [versionCode]. */
    private inner class FakeSelf(override val versionCode: Int) : SelfUpdate {
        override val versionName = "1.0.0 (test build $versionCode)"
        override val apkFile = File(tmp.root, "updates/pawtillery.apk")
        var installs = 0
        var installMessage: String? = null

        override fun install(file: File, onResult: (String?) -> Unit) {
            installs++
            onResult(installMessage)
        }
    }

    /** The internet, answering at once: update.json is [json] (null = offline). */
    private class FakeFetch(var json: String?, var downloadWorks: Boolean = true) : Fetch {
        var checks = 0
        var downloads = 0

        override fun text(url: String, onDone: (String?) -> Unit) {
            checks++
            onDone(json)
        }

        override fun file(url: String, dest: File, onProgress: (Float) -> Unit, onDone: (ok: Boolean) -> Unit) {
            downloads++
            if (downloadWorks) {
                dest.parentFile.mkdirs()
                dest.writeText("apk")
                onProgress(1f)
            }
            onDone(downloadWorks)
        }
    }

    private fun json(code: Int) =
        """{"versionCode": $code, "versionName": "1.0.0 (test build $code)", "apk": "https://example.com/pawtillery.apk", "notes": "More splats"}"""

    @Test
    fun readsUpdateJson() {
        val info = UpdateInfo.parse(json(7))!!
        assertEquals(7, info.versionCode)
        assertEquals("1.0.0 (test build 7)", info.versionName)
        assertEquals("https://example.com/pawtillery.apk", info.apkUrl)
        assertEquals("More splats", info.notes)
        assertNull(UpdateInfo.parse("<html>Not found</html>"))
        assertNull(UpdateInfo.parse("""{"versionName": "no code"}"""))
    }

    @Test
    fun aNewerBuildIsDownloadedThenOfferedForInstall() {
        val self = FakeSelf(5)
        val fetch = FakeFetch(json(7))
        val u = Updater(self, fetch)
        u.check(0.0)
        assertEquals(Updater.State.READY, u.state)
        assertEquals(7, u.available?.versionCode)
        assertEquals(1, fetch.downloads)
        assertEquals("nothing is installed without a tap", 0, self.installs)
        u.install()
        assertEquals(1, self.installs)
    }

    @Test
    fun theSameBuildIsUpToDate() {
        val fetch = FakeFetch(json(7))
        val u = Updater(FakeSelf(7), fetch)
        u.check(0.0)
        assertEquals(Updater.State.UP_TO_DATE, u.state)
        assertNull(u.available)
        assertEquals(0, fetch.downloads)
    }

    @Test
    fun aFailedCheckIsReportedAndRetriedLater() {
        val fetch = FakeFetch(null)
        val u = Updater(FakeSelf(5), fetch)
        u.check(0.0)
        assertEquals(Updater.State.FAILED, u.state)
        assertNotNull(u.message)
        u.checkIfDue(60.0)
        assertEquals("no retry a minute later", 1, fetch.checks)
        fetch.json = json(7)
        u.checkIfDue(Updater.RECHECK_SECONDS + 1.0)
        assertEquals(2, fetch.checks)
        assertEquals(Updater.State.READY, u.state)
    }

    @Test
    fun aFailedDownloadLeavesNothingToInstall() {
        val self = FakeSelf(5)
        val u = Updater(self, FakeFetch(json(7), downloadWorks = false))
        u.check(0.0)
        assertEquals(Updater.State.FAILED, u.state)
        u.install()
        assertEquals(0, self.installs)
    }

    @Test
    fun anUpdateIsDownloadedOnlyOnce() {
        val self = FakeSelf(5)
        val fetch = FakeFetch(json(7))
        Updater(self, fetch).check(0.0)
        // The game is closed and opened again before installing.
        val again = Updater(self, fetch)
        again.check(0.0)
        assertEquals(Updater.State.READY, again.state)
        assertEquals(1, fetch.downloads)
        // A newer build than the one downloaded is fetched fresh.
        fetch.json = json(8)
        Updater(self, fetch).check(0.0)
        assertEquals(2, fetch.downloads)
    }

    @Test
    fun theOldDownloadIsCleanedUpAfterUpdating() {
        val fetch = FakeFetch(json(7))
        Updater(FakeSelf(5), fetch).check(0.0)
        assertTrue(FakeSelf(5).apkFile.exists())
        // The player installed it; build 7 now runs.
        val u = Updater(FakeSelf(7), fetch)
        u.check(0.0)
        assertEquals(Updater.State.UP_TO_DATE, u.state)
        assertFalse(FakeSelf(7).apkFile.exists())
    }

    @Test
    fun theInstallerCanAskForSomethingFirst() {
        val self = FakeSelf(5).apply { installMessage = "Allow Pawtillery to install updates" }
        val u = Updater(self, FakeFetch(json(7)))
        u.check(0.0)
        u.install()
        assertEquals("Allow Pawtillery to install updates", u.message)
        assertEquals("still ready to try again", Updater.State.READY, u.state)
    }
}
