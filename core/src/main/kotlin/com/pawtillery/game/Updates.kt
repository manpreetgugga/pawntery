package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.JsonReader
import java.io.File
import java.io.IOException

/** A newer build, as described by the update.json that GitHub publishes next to each test APK. */
class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String) {
    companion object {
        /** Reads update.json; null if it isn't what we expect. */
        fun parse(json: String): UpdateInfo? = try {
            val v = JsonReader().parse(json)
            UpdateInfo(v.getInt("versionCode"), v.getString("versionName"), v.getString("apk"), v.getString("notes", ""))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * What a launcher provides so a test build installed from GitHub can update itself. Google Play
 * builds have none: Play updates them, and Play doesn't allow apps to install their own updates.
 */
interface SelfUpdate {
    val versionCode: Int
    val versionName: String

    /** Where a downloaded update waits until it's installed. */
    val apkFile: File

    /**
     * Hands [file] to the phone's installer, which asks the player to confirm. [onResult] gets a
     * message to show the player, or null when the installer opened. Called on the render thread.
     */
    fun install(file: File, onResult: (String?) -> Unit)
}

/** Downloads over the internet; callbacks arrive on the render thread. Tests use a fake. */
interface Fetch {
    fun text(url: String, onDone: (String?) -> Unit)

    fun file(url: String, dest: File, onProgress: (Float) -> Unit, onDone: (ok: Boolean) -> Unit)
}

/**
 * Keeps a test build up to date: looks for a newer build when the game starts (and again on the
 * menu every half hour), downloads it in the background, then offers to install it. Nothing is
 * installed without the player's tap, and the phone's installer asks once more.
 */
class Updater(private val self: SelfUpdate, private val fetch: Fetch, private val url: String = UPDATE_URL) {
    enum class State { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, FAILED }

    var state = State.IDLE
        private set

    /** The newer build, once one has been found. */
    var available: UpdateInfo? = null
        private set

    /** Download progress, 0 to 1. */
    var progress = 0f
        private set

    /** Something to tell the player: why a check or download failed, or what the installer needs. */
    var message: String? = null
        private set

    /** The player said "Later" to the install offer; the menu stops popping it up until the next check. */
    var dismissed = false

    private var lastCheck = Double.NEGATIVE_INFINITY

    val currentVersion: String get() = self.versionName

    /** Checks again if the last check was long enough ago. [now] is in seconds. */
    fun checkIfDue(now: Double) {
        if (state == State.READY || now - lastCheck < RECHECK_SECONDS) return
        check(now)
    }

    fun check(now: Double) {
        if (state == State.CHECKING || state == State.DOWNLOADING) return
        lastCheck = now
        state = State.CHECKING
        message = null
        fetch.text(url) { body ->
            val info = body?.let { UpdateInfo.parse(it) }
            when {
                info == null -> fail("Couldn't check for updates. Are you online?")
                info.versionCode <= self.versionCode -> upToDate()
                else -> {
                    available = info
                    dismissed = false
                    download(info)
                }
            }
        }
    }

    private fun upToDate() {
        state = State.UP_TO_DATE
        available = null
        // An update that was downloaded and has now been installed isn't needed any more.
        self.apkFile.delete()
        marker().delete()
    }

    private fun download(info: UpdateInfo) {
        if (self.apkFile.exists() && marker().exists() && marker().readText().trim() == info.versionCode.toString()) {
            state = State.READY
            return
        }
        state = State.DOWNLOADING
        progress = 0f
        marker().delete()
        fetch.file(info.apkUrl, self.apkFile, onProgress = { progress = it }) { ok ->
            if (ok) {
                marker().writeText(info.versionCode.toString())
                state = State.READY
            } else {
                fail("The update didn't finish downloading. It will try again later.")
            }
        }
    }

    /** Opens the phone's installer for the downloaded update. */
    fun install() {
        if (state != State.READY) return
        message = null
        self.install(self.apkFile) { message = it }
    }

    private fun fail(why: String) {
        state = State.FAILED
        message = why
    }

    /** Remembers which version the downloaded file is, so it isn't downloaded twice. */
    private fun marker() = File(self.apkFile.parentFile, self.apkFile.name + ".version")

    companion object {
        const val UPDATE_URL = "https://github.com/manpreetgugga/pawntery/releases/download/latest-build/update.json"
        const val RECHECK_SECONDS = 30.0 * 60.0
    }
}

/** [Fetch] over libGDX's networking, which works the same on Android and desktop. */
class GdxFetch : Fetch {
    override fun text(url: String, onDone: (String?) -> Unit) {
        Gdx.net.sendHttpRequest(request(url), object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                val body = if (response.status.statusCode == 200) response.resultAsString else null
                Gdx.app.postRunnable { onDone(body) }
            }

            override fun failed(t: Throwable) = Gdx.app.postRunnable { onDone(null) }

            override fun cancelled() = Gdx.app.postRunnable { onDone(null) }
        })
    }

    override fun file(url: String, dest: File, onProgress: (Float) -> Unit, onDone: (ok: Boolean) -> Unit) {
        Gdx.net.sendHttpRequest(request(url), object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                val ok = response.status.statusCode == 200 && save(response, dest, onProgress)
                Gdx.app.postRunnable { onDone(ok) }
            }

            override fun failed(t: Throwable) = Gdx.app.postRunnable { onDone(false) }

            override fun cancelled() = Gdx.app.postRunnable { onDone(false) }
        })
    }

    /** Streams the download into a temporary file and moves it into place only once it's complete. */
    private fun save(response: Net.HttpResponse, dest: File, onProgress: (Float) -> Unit): Boolean {
        val part = File(dest.parentFile, dest.name + ".part")
        return try {
            dest.parentFile?.mkdirs()
            val total = response.getHeader("Content-Length")?.toLongOrNull() ?: -1L
            var done = 0L
            var reported = 0
            response.resultAsStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent > reported) {
                            reported = percent
                            Gdx.app.postRunnable { onProgress(percent / 100f) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Got $done of $total bytes")
            dest.delete()
            part.renameTo(dest)
        } catch (e: IOException) {
            part.delete()
            false
        }
    }

    private fun request(url: String) = Net.HttpRequest(Net.HttpMethods.GET).apply {
        setUrl(url)
        setTimeOut(20_000)
        setFollowRedirects(true)
        setHeader("Cache-Control", "no-cache")
    }
}
