package com.brian.solwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayers

object WidgetUpdater {
    val REVISION = longPreferencesKey("widget_revision")
    val SHOW_SOLAR = booleanPreferencesKey("widget_show_solcast")
    val SHOW_TEMP = booleanPreferencesKey("widget_show_temperature")
    val SHOW_PRECIP = booleanPreferencesKey("widget_show_precipitation")
    val SHOW_RATES = booleanPreferencesKey("widget_show_dte_rates")
    val SHOW_BUY = booleanPreferencesKey("widget_show_buy_rate")
    val SHOW_SELL = booleanPreferencesKey("widget_show_sell_rate")

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val layers = AppStorage(appContext).chartLayersOnce()
        val manager = GlanceAppWidgetManager(appContext)
        val widget = ForecastWidget()
        val glanceIds = manager.getGlanceIds(ForecastWidget::class.java)
        val revision = System.currentTimeMillis()
        if (glanceIds.isNotEmpty()) {
            for (id in glanceIds) {
                updateAppWidgetState(appContext, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[REVISION] = revision
                        writeLayers(this, layers)
                    }
                }
                widget.update(appContext, id)
            }
        } else {
            widget.updateAll(appContext)
        }
        requestProviderUpdate(appContext)
    }

    fun layersFrom(prefs: androidx.datastore.preferences.core.Preferences, fallback: ChartLayers): ChartLayers {
        return ChartLayers(
            solcast = prefs[SHOW_SOLAR] ?: fallback.solcast,
            temperature = prefs[SHOW_TEMP] ?: fallback.temperature,
            precipitation = prefs[SHOW_PRECIP] ?: fallback.precipitation,
            buy = prefs[SHOW_BUY] ?: prefs[SHOW_RATES] ?: fallback.buy,
            sell = prefs[SHOW_SELL] ?: prefs[SHOW_RATES] ?: fallback.sell
        )
    }

    private fun writeLayers(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        layers: ChartLayers
    ) {
        prefs[SHOW_SOLAR] = layers.solcast
        prefs[SHOW_TEMP] = layers.temperature
        prefs[SHOW_PRECIP] = layers.precipitation
        prefs[SHOW_BUY] = layers.buy
        prefs[SHOW_SELL] = layers.sell
    }

    private fun requestProviderUpdate(context: Context) {
        val component = ComponentName(context, ForecastWidgetReceiver::class.java)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(context, ForecastWidgetReceiver::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
