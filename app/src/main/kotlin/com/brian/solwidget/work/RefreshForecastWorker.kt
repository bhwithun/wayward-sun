package com.brian.solwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.data.WeatherRepository
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

class RefreshWeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            WeatherRepository.get(applicationContext).refresh(force = false)
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object RefreshScheduler {
    private const val SOLCAST_WORK = "solcast_refresh"
    private const val WEATHER_WORK = "weather_refresh"

    fun ensure(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val solcast = PeriodicWorkRequestBuilder<RefreshForecastWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SOLCAST_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            solcast
        )
        val weather = PeriodicWorkRequestBuilder<RefreshWeatherWorker>(1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WEATHER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            weather
        )
    }
}
