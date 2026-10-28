package com.pawtillery.game.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Timer
import com.pawtillery.game.Fetch
import com.pawtillery.game.Platform
import com.pawtillery.game.PurchaseResult
import com.pawtillery.game.SelfUpdate
import java.io.File

/**
 * A pretend store and ad network for the desktop test build, so the Shop, the grown-up check and
 * the double-treats ad can be tried without a phone. Nothing here is real and no money moves.
 */
class DemoPlatform : Platform {
    private var bought = false

    override val plusPrice: String = "$2.99 (demo)"

    override fun buyPlus(onResult: (PurchaseResult) -> Unit) {
        bought = true
        later(0.5f) { onResult(PurchaseResult.BOUGHT) }
    }

    override fun restorePurchases(onResult: (owned: Boolean) -> Unit) = later(0.3f) { onResult(bought) }

    override val rewardedAdReady: Boolean = true

    override fun showRewardedAd(onReward: () -> Unit, onDone: () -> Unit) = later(1.5f) {
        onReward()
        onDone()
    }

    override fun openUrl(url: String) {
        Gdx.net.openURI(url)
    }

    private fun later(seconds: Float, action: () -> Unit) {
        Timer.schedule(object : Timer.Task() {
            override fun run() = Gdx.app.postRunnable(action)
        }, seconds)
    }
}

/** A pretend installed test build for the screenshot tour: build 5, with nothing really installed. */
class DemoSelfUpdate(dir: File) : SelfUpdate {
    override val versionCode = 5
    override val versionName = "1.0.0 (test build 5)"
    override val apkFile = File(dir, "pawtillery.apk")

    override fun install(file: File, onResult: (String?) -> Unit) =
        onResult("Desktop demo: on a phone, Android's installer opens here.")
}

/** A pretend GitHub that offers build 7 and "downloads" it over two seconds. */
class DemoFetch : Fetch {
    override fun text(url: String, onDone: (String?) -> Unit) = Gdx.app.postRunnable {
        onDone("""{"versionCode": 7, "versionName": "1.0.0 (test build 7)", "apk": "https://example.com/pawtillery.apk", "notes": "Breakable forts, three big throws and four power-ups"}""")
    }

    override fun file(url: String, dest: File, onProgress: (Float) -> Unit, onDone: (ok: Boolean) -> Unit) {
        for (i in 1..20) {
            Timer.schedule(object : Timer.Task() {
                override fun run() = Gdx.app.postRunnable {
                    onProgress(i / 20f)
                    if (i == 20) {
                        dest.parentFile.mkdirs()
                        dest.writeText("demo")
                        onDone(true)
                    }
                }
            }, i * 0.1f)
        }
    }
}
