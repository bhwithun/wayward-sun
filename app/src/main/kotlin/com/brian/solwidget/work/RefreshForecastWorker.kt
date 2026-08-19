package com.brian.solwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

class RefreshForecastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            ForecastRepository.get(applicationContext).refresh(force = false)
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object RefreshScheduler {
    private const val UNIQUE_NAME = "solcast_refresh"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshForecastWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
