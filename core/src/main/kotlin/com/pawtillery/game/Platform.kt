package com.pawtillery.game

import com.badlogic.gdx.Gdx
import kotlin.random.Random

enum class PurchaseResult { BOUGHT, CANCELLED, FAILED }

/**
 * What the game needs from the app store and the ad network. The game itself never talks to
 * Google Play or an ad SDK; a launcher supplies an implementation. Callbacks must arrive on the
 * render thread (wrap them in Gdx.app.postRunnable). See docs/MONETIZATION.md.
 */
interface Platform {
    /** Localized price of Pawtillery Plus, or null when the store isn't available (Plus is then hidden). */
    val plusPrice: String?

    fun buyPlus(onResult: (PurchaseResult) -> Unit)

    /** Asks the store whether Plus was bought before (new phone, reinstall). */
    fun restorePurchases(onResult: (owned: Boolean) -> Unit)

    /** True when a rewarded ad is loaded and can be shown right now. */
    val rewardedAdReady: Boolean

    /** Shows a rewarded ad; [onReward] runs only if it was watched to the end, [onDone] always runs after. */
    fun showRewardedAd(onReward: () -> Unit, onDone: () -> Unit)

    fun openUrl(url: String)
}

/** No store and no ads: the paid parts of the game stay hidden. The default until accounts are set up. */
class OfflinePlatform : Platform {
    override val plusPrice: String? = null

    override fun buyPlus(onResult: (PurchaseResult) -> Unit) = onResult(PurchaseResult.FAILED)

    override fun restorePurchases(onResult: (owned: Boolean) -> Unit) = onResult(false)

    override val rewardedAdReady: Boolean = false

    override fun showRewardedAd(onReward: () -> Unit, onDone: () -> Unit) = onDone()

    override fun openUrl(url: String) {
        Gdx.net.openURI(url)
    }
}

/**
 * A grown-up check before anything that spends money or leaves the game: a multiplication a young
 * child is unlikely to know, answered on a number pad.
 */
class ParentGate(private val rng: Random = Random) {
    var a = 0
        private set
    var b = 0
        private set
    var typed = ""
        private set

    init {
        newQuestion()
    }

    val question: String get() = "What is $a × $b?"

    fun newQuestion() {
        a = rng.nextInt(6, 10)
        b = rng.nextInt(6, 10)
        typed = ""
    }

    fun type(digit: Int) {
        if (typed.length < 3) typed += digit
    }

    fun clear() {
        typed = ""
    }

    /** True if the typed answer is right; a wrong answer gets a fresh question. */
    fun check(): Boolean {
        val ok = typed.toIntOrNull() == a * b
        if (!ok) newQuestion()
        return ok
    }

    companion object {
        const val PRIVACY_URL = "https://github.com/manpreetgugga/pawntery/blob/main/docs/privacy-policy.md"
    }
}
