package com.pawtillery.game.android

import com.badlogic.gdx.backends.android.AndroidApplication
import com.pawtillery.game.OfflinePlatform
import com.pawtillery.game.Platform

/**
 * Store builds (release): Google Play updates them, so there's no self-update here. Connect the
 * store and ads by returning `AndroidPlatform(activity)` instead (see docs/MONETIZATION.md).
 */
@Suppress("UNUSED_PARAMETER")
fun launcherPlatform(activity: AndroidApplication): Platform = OfflinePlatform()
