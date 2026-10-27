package com.pawtillery.game.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Timer
import com.pawtillery.game.Platform
import com.pawtillery.game.PurchaseResult

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
