package com.brian.solwidget.util

import android.content.Context
import android.widget.Toast
import com.brian.solwidget.BuildFacts
import com.brian.solwidget.data.AppStorage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val autoCheckStarted = AtomicBoolean(false)

fun currentBuildLabel(): BuildLabel =
    BuildLabel.resolve(BuildFacts.TAGS, BuildFacts.DIRTY, BuildFacts.SHORT_HASH, BuildFacts.COMMIT)

/**
 * Manual checks always report the result. An automatic check stays quiet unless
 * a newer release can be installed.
 */
suspend fun presentUpdate(context: Context, announceWhenCurrent: Boolean): UpdateDecision {
    val decision = withContext(Dispatchers.IO) {
        UpdateChecker.check(currentBuildLabel())
    }
    val apkUrl = decision.apkUrl?.takeIf { it.isNotBlank() }
    val updater = AppUpdater(context)
    if (apkUrl == null) {
        if (announceWhenCurrent) {
            toast(context, decision.message)
        }
        return decision
    }
    if (!updater.canInstallPackages()) {
        toast(context, "Allow Wayward Sun to install updates, then check again.")
        if (announceWhenCurrent) updater.openInstallPermissionSettings()
        return decision
    }
    toast(context, decision.message)
    val apk = withContext(Dispatchers.IO) { updater.download(apkUrl) }
    updater.promptInstall(apk)
    return decision
}

suspend fun runDailyUpdateCheck(context: Context) {
    if (!autoCheckStarted.compareAndSet(false, true)) return
    val storage = AppStorage(context.applicationContext)
    val now = System.currentTimeMillis()
    if (!UpdateSchedule.isDue(storage.updateCheckAt(), now)) return
    storage.stampUpdateCheck(now)
    try {
        presentUpdate(context, announceWhenCurrent = false)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The stamp already limits the next automatic attempt to tomorrow.
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
