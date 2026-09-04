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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var expandedPanel by remember { mutableStateOf<MetaPanel?>(null) }

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
                        StatusBanner("Showing sample output until the shared cache has Solcast data.")
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
                        onOpenSettings = onOpenSettings,
                        expandedPanel = expandedPanel,
                        onToggleExpand = { panel ->
                            expandedPanel = if (expandedPanel == panel) null else panel
                        }
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
                if (expandedPanel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SolColors.Navy)
                            .clickable { expandedPanel = null }
                            .padding(16.dp)
                    ) {
                        LayerMetaPanel(
                            layers = state.layers,
                            snapshot = snapshot,
                            weather = state.weather,
                            now = state.now,
                            onOpenSettings = onOpenSettings,
                            expandedPanel = expandedPanel,
                            onToggleExpand = { panel ->
                                expandedPanel = if (expandedPanel == panel) null else panel
                            },
                            showOnly = expandedPanel
                        )
                    }
                }
            }
        }
    }
}

private enum class MetaPanel { SOLAR, TEMP, PRECIP, RATES }

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
    onOpenSettings: () -> Unit,
    expandedPanel: MetaPanel?,
    onToggleExpand: (MetaPanel) -> Unit,
    showOnly: MetaPanel? = null
) {
    val expanded = showOnly != null
    val type = metaType(expanded)
    Column(
        modifier = if (expanded) Modifier.fillMaxSize() else Modifier,
        verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 8.dp)
    ) {
        if (layers.solcast && (showOnly == null || showOnly == MetaPanel.SOLAR)) {
            MetaCard(
                title = "Solar",
                expanded = expanded,
                onClick = { onToggleExpand(MetaPanel.SOLAR) }
            ) {
                LegendKeys(type, "● Live" to SolColors.Live, "● Forecast" to SolColors.Forecast, "● Now" to SolColors.Now)
                Text(snapshot.resourceId, color = SolColors.Ink, style = type.body)
                Text(Formatters.asOf(snapshot.fetchedAt, now), color = SolColors.Muted, style = type.caption)
                Text(
                    "Solcast ${snapshot.requestsUsed}/${snapshot.requestLimit} requests today",
                    color = SolColors.Muted,
                    style = type.caption
                )
            }
        }
        if (layers.temperature && (showOnly == null || showOnly == MetaPanel.TEMP)) {
            WeatherMetaCard(
                title = "Temperature",
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings,
                expanded = expanded,
                onClick = { onToggleExpand(MetaPanel.TEMP) },
                type = type
            ) {
                LegendKeys(
                    type,
                    "– Temp °F" to SolColors.Temp,
                    "– Below freeze" to SolColors.TempFreeze,
                    "– Above 90°F" to SolColors.TempHot
                )
            }
        }
        if (layers.precipitation && (showOnly == null || showOnly == MetaPanel.PRECIP)) {
            WeatherMetaCard(
                title = "Precipitation",
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings,
                expanded = expanded,
                onClick = { onToggleExpand(MetaPanel.PRECIP) },
                type = type
            ) {
                LegendKeys(type, "▮ Precip %" to SolColors.Precip.copy(alpha = 1f))
            }
        }
        if (layers.dteRates && (showOnly == null || showOnly == MetaPanel.RATES)) {
            MetaCard(
                title = "Rates",
                expanded = expanded,
                onClick = { onToggleExpand(MetaPanel.RATES) }
            ) {
                Text(
                    "${DteTou.PLAN_CODE} · ${DteTou.PLAN_NAME} · ${DteTou.RIDER}",
                    color = SolColors.Ink,
                    style = type.body
                )
                RateTable(type, expanded)
                Text(Formatters.asOf(DteTou.RATES_AS_OF, now, DteTou.ZONE), color = SolColors.Muted, style = type.caption)
                Text(
                    "Import effective includes PSCR and volumetric surcharges. Excludes \$8.50 service charge and sales tax.",
                    color = SolColors.Muted,
                    style = type.caption
                )
                Text(
                    "Export is Rider 18 outflow (power supply only, before then plus PSCR ${Formatters.cents(DteTou.PSCR_CENTS)}). Not 1:1 retail net metering. DTE rate book Sheet D-115, Case U-21860.",
                    color = SolColors.Muted,
                    style = type.caption
                )
            }
        }
    }
}

private data class MetaType(
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle
)

@Composable
private fun metaType(expanded: Boolean): MetaType {
    val t = MaterialTheme.typography
    return if (expanded) {
        MetaType(
            title = t.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
            body = t.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            caption = t.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp)
        )
    } else {
        MetaType(title = t.titleSmall, body = t.bodySmall, caption = t.labelSmall)
    }
}

@Composable
private fun WeatherMetaCard(
    title: String,
    weather: WeatherSnapshot?,
    now: Instant,
    onOpenSettings: () -> Unit,
    expanded: Boolean,
    onClick: () -> Unit,
    type: MetaType,
    legend: @Composable () -> Unit
) {
    MetaCard(title = title, expanded = expanded, onClick = onClick) {
        legend()
        val place = weather?.place
        if (place == null) {
            Text(
                text = "Set a city or ZIP in Settings for local weather.",
                color = SolColors.Muted,
                style = type.body
            )
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        } else {
            Text(place.label, color = SolColors.Ink, style = type.body)
            weather.errorMessage?.let { message ->
                Text(message, color = SolColors.Forecast, style = type.body)
            }
            Text(Formatters.asOf(weather.fetchedAt, now), color = SolColors.Muted, style = type.caption)
        }
    }
}

@Composable
private fun MetaCard(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val type = metaType(expanded)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxSize() else Modifier)
            .clip(RoundedCornerShape(if (expanded) 20.dp else 14.dp))
            .background(SolColors.Panel)
            .clickable(onClick = onClick)
            .padding(if (expanded) 20.dp else 12.dp)
            .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 4.dp)
    ) {
        Text(text = title, color = SolColors.Ink, style = type.title)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendKeys(type: MetaType, vararg keys: Pair<String, Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        keys.forEach { (label, color) ->
            Text(label, color = color, style = type.body)
        }
    }
}

@Composable
private fun RateTable(type: MetaType, expanded: Boolean) {
    val headerStyle = type.caption.copy(fontWeight = FontWeight.SemiBold)
    val cellStyle = type.body
    val rowPad = if (expanded) 14.dp else 6.dp
    val stripe = if (expanded) SolColors.TableStripe else SolColors.PanelAlt
    val even = if (expanded) SolColors.PanelAlt else Color.Transparent
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(even),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        RateTableRow(
            label = "",
            off = "Off-peak",
            mid = "Mid-peak",
            peak = "Peak",
            labelColor = SolColors.Muted,
            offColor = SolColors.Live.copy(alpha = 0.75f),
            midColor = SolColors.Forecast.copy(alpha = 0.85f),
            peakColor = SolColors.TouPeak.copy(alpha = 1f),
            textStyle = headerStyle,
            rowColor = stripe,
            verticalPadding = rowPad
        )
        RateTableRow(
            label = "Import base",
            off = Formatters.cents(DteTou.BASE_OFF_PEAK_CENTS),
            mid = Formatters.cents(DteTou.BASE_MID_PEAK_CENTS),
            peak = Formatters.cents(DteTou.BASE_PEAK_CENTS),
            textStyle = cellStyle,
            rowColor = even,
            verticalPadding = rowPad
        )
        RateTableRow(
            label = "Import effective",
            off = Formatters.cents(DteTou.OFF_PEAK_CENTS),
            mid = Formatters.cents(DteTou.MID_PEAK_CENTS),
            peak = Formatters.cents(DteTou.PEAK_CENTS),
            textStyle = cellStyle,
            rowColor = stripe,
            verticalPadding = rowPad
        )
        RateTableRow(
            label = "Export (R18)",
            off = Formatters.cents(DteTou.OUTFLOW_OFF_PEAK_CENTS),
            mid = Formatters.cents(DteTou.OUTFLOW_MID_PEAK_CENTS),
            peak = Formatters.cents(DteTou.OUTFLOW_PEAK_CENTS),
            textStyle = cellStyle,
            rowColor = even,
            verticalPadding = rowPad
        )
        RateTableRow(
            label = "Export + PSCR",
            off = Formatters.cents(DteTou.OUTFLOW_OFF_PEAK_WITH_PSCR_CENTS),
            mid = Formatters.cents(DteTou.OUTFLOW_MID_PEAK_WITH_PSCR_CENTS),
            peak = Formatters.cents(DteTou.OUTFLOW_PEAK_WITH_PSCR_CENTS),
            textStyle = cellStyle,
            rowColor = stripe,
            verticalPadding = rowPad
        )
    }
}

@Composable
private fun RateTableRow(
    label: String,
    off: String,
    mid: String,
    peak: String,
    labelColor: Color = SolColors.Muted,
    offColor: Color = SolColors.Ink,
    midColor: Color = SolColors.Ink,
    peakColor: Color = SolColors.Ink,
    textStyle: TextStyle,
    rowColor: Color,
    verticalPadding: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .padding(horizontal = 8.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = labelColor, style = textStyle, modifier = Modifier.weight(1.15f), maxLines = 2)
        Text(off, color = offColor, style = textStyle, modifier = Modifier.weight(0.95f), maxLines = 2)
        Text(mid, color = midColor, style = textStyle, modifier = Modifier.weight(0.95f), maxLines = 2)
        Text(peak, color = peakColor, style = textStyle, modifier = Modifier.weight(0.85f), maxLines = 2)
    }
}


