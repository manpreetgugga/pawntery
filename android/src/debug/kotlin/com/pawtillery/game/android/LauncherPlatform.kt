package com.pawtillery.game.android

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.pawtillery.game.OfflinePlatform
import com.pawtillery.game.Platform
import com.pawtillery.game.SelfUpdate
import java.io.File

/** Test builds (debug): no store or ads yet, but they keep themselves up to date from GitHub. */
fun launcherPlatform(activity: AndroidApplication): Platform = TestBuildPlatform(activity)

private class TestBuildPlatform(activity: Activity) : Platform by OfflinePlatform() {
    override val selfUpdate: SelfUpdate = ApkSelfUpdate(activity)
}

/** Installs a downloaded test build through the phone's own installer, which asks the player first. */
private class ApkSelfUpdate(private val activity: Activity) : SelfUpdate {
    @Suppress("DEPRECATION")
    private val info = activity.packageManager.getPackageInfo(activity.packageName, 0)

    override val versionCode: Int =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode

    override val versionName: String = info.versionName ?: "?"

    override val apkFile: File = UpdateFileProvider.file(activity)

    override fun install(file: File, onResult: (String?) -> Unit) {
        activity.runOnUiThread {
            val message = try {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    // The first time, Android wants the player to allow Pawtillery to install apps.
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    "Allow Pawtillery to install updates, then come back and tap Install."
                } else {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(UpdateFileProvider.uri(activity), UpdateFileProvider.MIME)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    null
                }
            } catch (e: Exception) {
                "Couldn't open the installer. Download the update from GitHub instead."
            }
            Gdx.app.postRunnable { onResult(message) }
        }
    }
}

/**
 * Shares the one downloaded update file, read-only, with the phone's installer. Android doesn't
 * let apps hand out plain file paths, so the installer reads it through this provider.
 */
class UpdateFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(context!!), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(FILE_NAME, file(context!!).length()))
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val MIME = "application/vnd.android.package-archive"
        private const val FILE_NAME = "pawtillery.apk"

        fun file(context: Context): File = File(File(context.cacheDir, "updates"), FILE_NAME)

        fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.updates/$FILE_NAME")
    }
}
