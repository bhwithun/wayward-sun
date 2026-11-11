package com.brian.solwidget.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.brian.solwidget.R
import java.io.File
import kotlin.concurrent.thread

object AppUpdate {
    sealed class Check {
        class Update(val offer: ReleaseOffer) : Check()
        data object Current : Check()
        data object Failed : Check()
    }

    private const val APK_TYPE = "application/vnd.android.package-archive"
    private const val PREFS = "app_update"
    private const val CHECKED_AT = "checked_at"
    private const val VERSION = "version_name"
    private const val URL = "apk_url"
    private const val DISMISSED = "dismissed_version"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val waiters = mutableListOf<(ReleaseOffer?) -> Unit>()
    private val promptLock = Any()
    private var refreshInFlight = false
    private var promptedVersion: String? = null
    private var installInFlight = false

    fun installedVersionName(context: Context): String = installedPackage(context).versionName.orEmpty()

    fun offer(context: Context): ReleaseOffer? {
        val stored = readOffer(context) ?: return null
        if (!isNewerRelease(installedVersionName(context), stored.versionName)) return null
        return stored
    }

    fun dismiss(context: Context, versionName: String) {
        prefs(context).edit().putString(DISMISSED, versionName).apply()
    }

    fun claimPrompt(context: Context, offer: ReleaseOffer): Boolean {
        synchronized(promptLock) {
            val dismissed = prefs(context).getString(DISMISSED, null)
            if (!shouldPromptForUpdate(offer.versionName, dismissed, promptedVersion)) return false
            promptedVersion = offer.versionName
            return true
        }
    }

    fun refreshAsync(activity: Activity, onResult: (ReleaseOffer?) -> Unit) {
        val app = activity.applicationContext
        fun deliver(offer: ReleaseOffer?) {
            onMain {
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                onResult(offer)
            }
        }
        val checkedAt = prefs(app).getLong(CHECKED_AT, 0L)
        if (!isReleaseCheckDue(checkedAt, System.currentTimeMillis())) {
            deliver(offer(app))
            return
        }
        synchronized(waiters) {
            waiters.add { result -> deliver(result) }
            if (refreshInFlight) return
            refreshInFlight = true
        }
        thread(name = "wayward-sun-update") {
            try {
                fetchAndStore(app)
            } catch (_: Exception) {
            }
            val result = offer(app)
            val pending = synchronized(waiters) {
                refreshInFlight = false
                waiters.toList().also { waiters.clear() }
            }
            pending.forEach { it(result) }
        }
    }

    fun checkNow(activity: Activity, onResult: (Check) -> Unit) {
        val app = activity.applicationContext
        thread(name = "wayward-sun-update") {
            val result = try {
                if (!fetchAndStore(app)) Check.Failed
                else offer(app)?.let { Check.Update(it) } ?: Check.Current
            } catch (_: Exception) {
                Check.Failed
            }
            onMain {
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                onResult(result)
            }
        }
    }

    fun startInstall(activity: Activity, offer: ReleaseOffer) {
        if (installInFlight) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow, Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        installInFlight = true
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        thread(name = "wayward-sun-update") {
            val file = try {
                ReleaseLookup.downloadApk(offer.apkUrl, activity.applicationContext.cacheDir)
            } catch (_: Exception) {
                null
            }
            onMain {
                installInFlight = false
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                if (file == null || !downloadedApkCanUpgrade(activity, file) || !launchInstaller(activity, file)) {
                    Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchAndStore(context: Context): Boolean {
        return when (val result = ReleaseLookup.queryLatest()) {
            is LatestRelease.Found -> {
                val installed = installedVersionName(context)
                val offer = releaseOffer(result.release.version, result.release.downloadUrl, installed)
                if (offer == null && isNewerRelease(installed, result.release.version)) return false
                writeCheck(context, System.currentTimeMillis(), offer)
                true
            }
            LatestRelease.Unavailable -> {
                prefs(context).edit().putLong(CHECKED_AT, System.currentTimeMillis()).apply()
                false
            }
            LatestRelease.Unreadable -> false
        }
    }

    private fun downloadedApkCanUpgrade(context: Context, file: File): Boolean {
        val archived = archiveInfo(context, file) ?: return false
        val installed = installedPackage(context)
        return isInstallableUpgrade(
            archived.packageName,
            archived.versionCode(),
            installed.versionCode(),
        )
    }

    private fun launchInstaller(activity: Activity, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun installedPackage(context: Context): PackageInfo {
        val manager = context.packageManager
        val name = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(name, 0)
        }
    }

    private fun archiveInfo(context: Context, file: File): PackageInfo? {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageArchiveInfo(file.absolutePath, 0)
        }
    }

    private fun readOffer(context: Context): ReleaseOffer? {
        val version = prefs(context).getString(VERSION, null)?.trim().orEmpty()
        val url = prefs(context).getString(URL, null)?.trim().orEmpty()
        if (version.isEmpty() || !url.startsWith("https://")) return null
        return ReleaseOffer(version, url)
    }

    private fun writeCheck(context: Context, checkedAtMillis: Long, offer: ReleaseOffer?) {
        val editor = prefs(context).edit().putLong(CHECKED_AT, checkedAtMillis)
        if (offer == null) {
            editor.remove(VERSION).remove(URL)
        } else {
            editor.putString(VERSION, offer.versionName).putString(URL, offer.apkUrl)
        }
        editor.apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private fun PackageInfo.versionCode(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
