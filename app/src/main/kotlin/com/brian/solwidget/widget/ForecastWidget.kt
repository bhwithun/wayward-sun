package com.brian.solwidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.brian.solwidget.MainActivity
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.data.WeatherRepository
import com.brian.solwidget.ui.components.ChartBitmapRenderer
import java.time.Instant
import java.time.ZoneId

class ForecastWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = ForecastRepository.get(context).loadSnapshot()
        val weather = WeatherRepository.get(context).loadSnapshot()
        val layers = AppStorage(context).chartLayersOnce()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val weatherPoints = weather.alignedWithPower(now, zone)
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        provideContent {
            val glanceLayers = WidgetUpdater.layersFrom(currentState<Preferences>(), layers)
            GlanceTheme {
                WidgetContent(
                    snapshot = snapshot,
                    weather = weatherPoints,
                    layers = glanceLayers,
                    openApp = openApp
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    snapshot: ForecastSnapshot,
    weather: List<WeatherPoint>,
    layers: ChartLayers,
    openApp: Action
) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val size = LocalSize.current
    val headerAndPadding = 46.dp
    val chartHeightDp = (size.height - headerAndPadding).coerceAtLeast(72.dp)
    val chartWidth = (size.width.value * 2.75f).toInt().coerceIn(240, 1000)
    val chartHeightPx = (chartHeightDp.value * 2.75f).toInt().coerceIn(140, 640)
    val (from, to) = ForecastSnapshot.range(now, zone)
    val chartPoints = snapshot.displayPoints(now, zone)
    val chart = ChartBitmapRenderer.render(
        points = chartPoints,
        now = now,
        width = chartWidth,
        height = chartHeightPx,
        rangeFrom = from,
        rangeTo = to,
        weather = weather,
        layers = layers,
        isDemo = snapshot.isDemo
    )

    val muted = ColorProvider(Color(0xFF9AA8BF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(0xFF152036)))
            .clickable(openApp)
            .padding(14.dp)
    ) {
        Text(
            "Wayward Sun",
            style = TextStyle(color = muted, fontSize = 12.sp)
        )
        Spacer(GlanceModifier.height(6.dp))
        Image(
            provider = ImageProvider(chart),
            contentDescription = "Wayward Sun live and forecast power",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(chartHeightDp)
        )
        if (snapshot.isDemo) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Sample data · waiting for shared cache",
                style = TextStyle(color = muted, fontSize = 11.sp)
            )
        }
    }
}

class ForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForecastWidget()
}
