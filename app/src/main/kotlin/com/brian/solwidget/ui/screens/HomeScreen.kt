package com.brian.solwidget.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DteTou
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.WeatherSnapshot
import com.brian.solwidget.ui.components.PowerChart
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.Formatters
import com.brian.solwidget.viewmodel.HomeViewModel
import com.brian.solwidget.viewmodel.isSolcastQuotaMessage
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snapshot = state.snapshot
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeToast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wayward Sun") },
                actions = {
                    TextButton(onClick = { viewModel.refresh(force = true) }) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SolColors.Navy,
                    titleContentColor = SolColors.Ink
                )
            )
        },
        containerColor = SolColors.Navy
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (snapshot == null && state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (snapshot != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    LayerToggles(
                        layers = state.layers,
                        onToggle = viewModel::setLayer,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    snapshot.errorMessage
                        ?.takeUnless { it.isSolcastQuotaMessage() }
                        ?.let { message ->
                            StatusBanner(message)
                        }
                    if (snapshot.isDemo && snapshot.errorMessage == null) {
                        StatusBanner("Showing sample output until Solcast data is available.")
                    }

                    Text(
                        text = "2 days before through 2 days after",
                        style = MaterialTheme.typography.titleMedium,
                        color = SolColors.Ink
                    )

                    val (rangeFrom, rangeTo) = ForecastSnapshot.range(state.now, zone)
                    val chartPoints = snapshot.displayPoints(state.now, zone)
                    val weatherPoints = state.weather?.alignedWithPower(state.now, zone).orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SolColors.Panel)
                            .padding(8.dp)
                    ) {
                        PowerChart(
                            points = chartPoints,
                            now = state.now,
                            weather = weatherPoints,
                            rangeFrom = rangeFrom,
                            rangeTo = rangeTo,
                            layers = state.layers
                        )
                    }

                    LayerMetaPanel(
                        layers = state.layers,
                        snapshot = snapshot,
                        weather = state.weather,
                        now = state.now,
                        onOpenSettings = onOpenSettings
                    )

                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SolColors.PanelAlt)
            .padding(12.dp),
        color = SolColors.Forecast,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun LayerToggles(
    layers: ChartLayers,
    onToggle: (ChartLayer, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LayerChip("Solar", layers.solcast, Modifier.weight(1f)) { onToggle(ChartLayer.SOLCAST, it) }
        LayerChip("Temp", layers.temperature, Modifier.weight(1f)) { onToggle(ChartLayer.TEMPERATURE, it) }
        LayerChip("Precip", layers.precipitation, Modifier.weight(1f)) { onToggle(ChartLayer.PRECIPITATION, it) }
        LayerChip("Rates", layers.dteRates, Modifier.weight(1f)) { onToggle(ChartLayer.DTE_RATES, it) }
    }
}

@Composable
private fun LayerChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SolColors.PanelAlt else SolColors.Panel)
            .clickable { onClick(!selected) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) SolColors.Ink else SolColors.Muted,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun LayerMetaPanel(
    layers: ChartLayers,
    snapshot: ForecastSnapshot,
    weather: WeatherSnapshot?,
    now: Instant,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layers.solcast) {
            MetaCard(title = "Solar") {
                LegendKeys(
                    "● Live" to SolColors.Live,
                    "● Forecast" to SolColors.Forecast,
                    "● Now" to SolColors.Now
                )
                Text(
                    text = snapshot.resourceId,
                    color = SolColors.Ink,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = Formatters.asOf(snapshot.fetchedAt, now),
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Solcast ${snapshot.requestsUsed}/${snapshot.requestLimit} requests today",
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (layers.temperature) {
            WeatherMetaCard(
                title = "Temperature",
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings
            ) {
                LegendKeys(
                    "– Temp °F" to SolColors.Temp,
                    "– Below freeze" to SolColors.TempFreeze,
                    "– Above 90°F" to SolColors.TempHot
                )
            }
        }
        if (layers.precipitation) {
            WeatherMetaCard(
                title = "Precipitation",
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings
            ) {
                LegendKeys("▮ Precip %" to SolColors.Precip.copy(alpha = 1f))
            }
        }
        if (layers.dteRates) {
            MetaCard(title = "Rates") {
                Text(
                    text = "${DteTou.PLAN_CODE} · ${DteTou.PLAN_NAME} · ${DteTou.RIDER}",
                    color = SolColors.Ink,
                    style = MaterialTheme.typography.bodySmall
                )
                RateLine(
                    label = "DTE base",
                    off = DteTou.BASE_OFF_PEAK_CENTS,
                    mid = DteTou.BASE_MID_PEAK_CENTS,
                    peak = DteTou.BASE_PEAK_CENTS
                )
                RateLine(
                    label = "Your effective",
                    off = DteTou.OFF_PEAK_CENTS,
                    mid = DteTou.MID_PEAK_CENTS,
                    peak = DteTou.PEAK_CENTS
                )
                Text(
                    text = Formatters.asOf(DteTou.RATES_AS_OF, now, DteTou.ZONE),
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Effective includes PSCR and volumetric surcharges. Excludes \$8.50 service charge and sales tax.",
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WeatherMetaCard(
    title: String,
    weather: WeatherSnapshot?,
    now: Instant,
    onOpenSettings: () -> Unit,
    legend: @Composable () -> Unit
) {
    MetaCard(title = title) {
        legend()
        val place = weather?.place
        if (place == null) {
            Text(
                text = "Set a city or ZIP in Settings for local weather.",
                color = SolColors.Muted,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        } else {
            Text(
                text = place.label,
                color = SolColors.Ink,
                style = MaterialTheme.typography.bodySmall
            )
            weather.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = SolColors.Forecast,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = Formatters.asOf(weather.fetchedAt, now),
                color = SolColors.Muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MetaCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SolColors.Panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = SolColors.Ink,
            style = MaterialTheme.typography.titleSmall
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendKeys(vararg keys: Pair<String, Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        keys.forEach { (label, color) ->
            Text(label, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateLine(label: String, off: Double, mid: Double, peak: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = SolColors.Muted,
            style = MaterialTheme.typography.labelSmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Off-peak ${Formatters.cents(off)}",
                color = SolColors.Live.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Mid-peak ${Formatters.cents(mid)}",
                color = SolColors.Forecast.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Peak ${Formatters.cents(peak)}",
                color = SolColors.TouPeak.copy(alpha = 1f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


