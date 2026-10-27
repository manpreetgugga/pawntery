# Making money with Pawtillery

## The model

| What | Price | Why |
|---|---|---|
| The whole game | Free | Matches, 2 players, Trick Shots and all items are free. Everything can be earned by playing. |
| **Pawtillery Plus** | One-time, suggested **$2.99 / ₹249** | All 5 pets right away, no ads, double treats from every game and star. |
| **Rewarded ad** | Free, optional | "Watch an ad: double it" on the result screen doubles that game's treats. At most 5 a day, never with Plus. |

There are **no banner ads, no pop-up ads between games, no loot boxes, no timers and no energy**. In a game that children play, that is what earns good reviews from parents and keeps the app safe under Google Play's Families policy. The one purchase is easy for a parent to say yes to.

## How it is built

The game never talks to Google Play or AdMob directly. It uses one small interface, `Platform` (`core/src/main/kotlin/com/pawtillery/game/Platform.kt`):

- `plusPrice`, `buyPlus`, `restorePurchases`: the Plus purchase.
- `rewardedAdReady`, `showRewardedAd`: the optional ad.
- `openUrl`: for the privacy policy link.

Today the Android app uses `OfflinePlatform`. **The Shop shows "Coming soon" and the ad button stays hidden**, so the game is complete and safe to publish before any accounts exist. The desktop test build uses `DemoPlatform`, which pretends to sell Plus and show ads so every screen can be tried.

Already done inside the game:

- **Grown-up check** before buying, restoring, or opening the privacy policy (a multiplication question on a number pad).
- Plus unlocks all pets, removes the ad button and doubles treats. It is restored automatically at launch on a new phone.
- A daily cap on rewarded ads.

## Turning it on (about an afternoon)

1. **Google Play Console** (one-time $25 developer fee): create the app, then under *Monetize > Products > In-app products* create a one-time product with ID **`pawtillery_plus`** and set its price.
2. **AdMob** (free): create an app and one **Rewarded** ad unit. While developing, use Google's test IDs. Real ad IDs on your own phone can get your account suspended.
3. In `android/build.gradle.kts`, add the libraries (take current versions from Google's release notes):
   ```kotlin
   implementation("com.android.billingclient:billing-ktx:<latest>")
   implementation("com.google.android.gms:play-services-ads:<latest>")
   ```
4. In `android/AndroidManifest.xml`, inside `<application>`, add your AdMob **app** ID:
   ```xml
   <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXXXXXX~YYYYYYYY" />
   ```
5. Add `AndroidPlatform.kt` (below) next to `AndroidLauncher.kt` and start the game with it:
   ```kotlin
   initialize(Pawtillery(AndroidPlatform(this)), config)
   ```
6. Upload a build to the *Internal testing* track, add yourself as a license tester, and buy Plus with a test card.

> The code below was written against Play Billing 7 and Google Mobile Ads 23 and **has not been compiled in this repository**. This build environment can't download Google's Android libraries. Android Studio will flag anything that changed in newer versions.

```kotlin
package com.pawtillery.game.android

import com.android.billingclient.api.*
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.pawtillery.game.Platform
import com.pawtillery.game.PurchaseResult

class AndroidPlatform(private val activity: AndroidApplication) : Platform, PurchasesUpdatedListener {
    private val billing = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()
    private var plus: ProductDetails? = null
    private var pendingBuy: ((PurchaseResult) -> Unit)? = null
    private var rewarded: RewardedAd? = null

    init {
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) loadProduct()
            }
            override fun onBillingServiceDisconnected() = Unit
        })
        // Everyone is treated as a child for ads: child-directed, rated G, and so not personalized.
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
        MobileAds.initialize(activity) { loadAd() }
    }

    private fun loadProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PLUS_ID).setProductType(BillingClient.ProductType.INAPP).build()
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, list ->
            plus = list.firstOrNull()
        }
    }

    override val plusPrice: String? get() = plus?.oneTimePurchaseOfferDetails?.formattedPrice

    override fun buyPlus(onResult: (PurchaseResult) -> Unit) {
        val details = plus ?: return onResult(PurchaseResult.FAILED)
        pendingBuy = onResult
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()))
            .build()
        activity.runOnUiThread { billing.launchBillingFlow(activity, params) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val bought = purchases?.firstOrNull { PLUS_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (bought != null && !bought.isAcknowledged) {
            billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(bought.purchaseToken).build()) {}
        }
        val outcome = when {
            bought != null -> PurchaseResult.BOUGHT
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.CANCELLED
            else -> PurchaseResult.FAILED
        }
        val callback = pendingBuy ?: return
        pendingBuy = null
        Gdx.app.postRunnable { callback(outcome) }
    }

    override fun restorePurchases(onResult: (owned: Boolean) -> Unit) {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, list ->
            val owned = list.any { PLUS_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            Gdx.app.postRunnable { onResult(owned) }
        }
    }

    private fun loadAd() = activity.runOnUiThread {
        RewardedAd.load(activity, REWARDED_AD_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null }
        })
    }

    override val rewardedAdReady: Boolean get() = rewarded != null

    override fun showRewardedAd(onReward: () -> Unit, onDone: () -> Unit) {
        val ad = rewarded ?: return onDone()
        rewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { Gdx.app.postRunnable(onDone); loadAd() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { Gdx.app.postRunnable(onDone); loadAd() }
        }
        activity.runOnUiThread { ad.show(activity) { Gdx.app.postRunnable(onReward) } }
    }

    override fun openUrl(url: String) {
        Gdx.net.openURI(url)
    }

    private companion object {
        const val PLUS_ID = "pawtillery_plus"
        // Google's public test unit. Replace with your own rewarded ad unit ID before release.
        const val REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"
    }
}
```

## Google Play: forms to fill in correctly

- **Target audience and content**: choose the age groups including **under 13**. The app then follows the **Families policy**. That is fine: AdMob is a Families self-certified ad SDK, ads are child-directed, and there are no external links without a grown-up check.
- **Ads**: declare "Contains ads" once rewarded ads are on.
- **Data safety**: the game itself collects nothing. Declare what the Google Play Billing and AdMob SDKs collect, following Google's own guidance for those SDKs (AdMob in child-directed mode still processes things like IP address for delivery and fraud prevention).
- **Content rating**: cartoon pets throwing mud, no blood and no real violence. Expect *Everyone* / PEGI 3.
- **Privacy policy URL**: required. Publish `docs/privacy-policy.md` somewhere public (for example GitHub Pages, or make the repository public), fill in your support email, and update `ParentGate.PRIVACY_URL` in `Platform.kt` to that address.

## Ideas for later

- Seasonal cosmetic packs (hats, fort skins). Small, optional, and appealing to every age.
- More Trick Shot level packs, free with an update, to bring players back.
- A "Family" price promotion during school holidays.
