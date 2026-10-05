package com.brian.solwidget.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.solwidget.GitHash
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.DteTou
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.WeatherSnapshot
import com.brian.solwidget.data.chartFor
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.data.liveSolarPoints
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.ui.components.PowerChart
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.Formatters
import com.brian.solwidget.util.IntentUtils
import com.brian.solwidget.viewmodel.HomeTab
import com.brian.solwidget.viewmodel.HomeViewModel
import com.brian.solwidget.viewmodel.isSolcastQuotaMessage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val showLiveOverlay = state.tab == HomeTab.LIVE && expandedPanel != null

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeToast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Wayward Sun")
                        if (GitHash.VALUE.isNotBlank()) {
                            Text(
                                text = GitHash.VALUE,
                                color = SolColors.Muted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        IntentUtils.openUrl(context, "https://sun.brianandkathi.com")
                    }) {
                        Text("Web App")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                HomeTabs(
                    tab = state.tab,
                    onSelect = viewModel::showTab,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LayerToggles(
                    layers = state.layers,
                    onToggle = viewModel::setLayer,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.tab == HomeTab.LIVE) {
                            LivePane(
                                snapshot = snapshot,
                                weather = state.weather,
                                livePast = state.livePast,
                                layers = state.layers,
                                now = state.now,
                                zone = zone,
                                isLoading = state.isLoading,
                                onOpenSettings = onOpenSettings,
                                expandedPanel = expandedPanel,
                                onToggleExpand = { panel ->
                                    expandedPanel = if (expandedPanel == panel) null else panel
                                }
                            )
                        } else {
                            HistoryPane(
                                month = state.historyMonth,
                                selectedWeek = state.selectedWeek,
                                history = state.history,
                                historyKey = state.historyKey,
                                historyLoading = state.historyLoading,
                                historyError = state.historyError,
                                layers = state.layers,
                                now = state.now,
                                onShiftMonth = viewModel::shiftHistoryMonth,
                                onSelectWeek = viewModel::selectHistoryWeek
                            )
                        }
                    }
                    if (showLiveOverlay && snapshot != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SolColors.Navy)
                                .clickable { expandedPanel = null }
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
}

@Composable
private fun HomeTabs(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LayerChip("Live", tab == HomeTab.LIVE, Modifier.weight(1f)) {
            if (it) onSelect(HomeTab.LIVE)
        }
        LayerChip("History", tab == HomeTab.HISTORY, Modifier.weight(1f)) {
            if (it) onSelect(HomeTab.HISTORY)
        }
    }
}

@Composable
private fun LivePane(
    snapshot: ForecastSnapshot?,
    weather: WeatherSnapshot?,
    livePast: List<PowerPoint>?,
    layers: ChartLayers,
    now: Instant,
    zone: ZoneId,
    isLoading: Boolean,
    onOpenSettings: () -> Unit,
    expandedPanel: MetaPanel?,
    onToggleExpand: (MetaPanel) -> Unit
) {
    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) CircularProgressIndicator()
        }
        return
    }
    snapshot.errorMessage
        ?.takeUnless { it.isSolcastQuotaMessage() }
        ?.let { message -> StatusBanner(message) }
    if (snapshot.isDemo && snapshot.errorMessage == null) {
        StatusBanner("Showing sample output until the shared cache has Solcast data.")
    }
    Text(
        text = "2 days before through 2 days after",
        style = MaterialTheme.typography.titleMedium,
        color = SolColors.Ink
    )
    val (rangeFrom, rangeTo) = ForecastSnapshot.range(now, zone)
    ChartFrame {
        PowerChart(
            points = liveSolarPoints(livePast, snapshot.points, rangeFrom, rangeTo, now),
            now = now,
            weather = weather?.alignedWithPower(now, zone).orEmpty(),
            rangeFrom = rangeFrom,
            rangeTo = rangeTo,
            layers = layers,
            sunDays = weather?.sunDays.orEmpty(),
            isDemo = snapshot.isDemo && livePast.isNullOrEmpty()
        )
    }
    LayerMetaPanel(
        layers = layers,
        snapshot = snapshot,
        weather = weather,
        now = now,
        onOpenSettings = onOpenSettings,
        expandedPanel = expandedPanel,
        onToggleExpand = onToggleExpand
    )
    if (isLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun HistoryPane(
    month: YearMonth?,
    selectedWeek: LocalDate?,
    history: HistoryPage?,
    historyKey: String?,
    historyLoading: Boolean,
    historyError: String?,
    layers: ChartLayers,
    now: Instant,
    onShiftMonth: (Int) -> Unit,
    onSelectWeek: (LocalDate) -> Unit
) {
    val today = DetroitCalendar.today(now)
    val shownMonth = month ?: YearMonth.from(today)
    val weeks = DetroitCalendar.monthWeeks(shownMonth)
    val week = weeks.find { it.id == selectedWeek }
        ?: weeks.find { days -> days.days.any { it.date == today } }
        ?: weeks.firstOrNull()
    val (fetchFrom, fetchTo) = DetroitCalendar.monthFetchRange(weeks)
    val pageMatches = historyKey == historyFetchKey(fetchFrom, fetchTo)
    historyError?.let { StatusBanner(it) }
    if (week != null) {
        val (from, to) = DetroitCalendar.weekRange(week.id)
        val chart = if (pageMatches && history != null) history.chartFor(from, to, now) else null
        ChartFrame {
            if (chart != null) {
                PowerChart(
                    points = chart.points,
                    now = now,
                    weather = chart.weather,
                    rangeFrom = from,
                    rangeTo = to,
                    layers = layers,
                    sunDays = chart.sunDays,
                    zone = DetroitCalendar.ZONE
                )
            }
            if (chart == null && historyLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        if (pageMatches && history != null && chart != null &&
            chart.points.isEmpty() && chart.weather.isEmpty() && historyError == null
        ) {
            Text(
                text = "No stored intervals in this range yet.",
                color = SolColors.Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    HistoryCalendar(
        month = shownMonth,
        weeks = weeks,
        selectedWeek = week?.id,
        today = today,
        onShiftMonth = onShiftMonth,
        onSelectWeek = onSelectWeek
    )
}

@Composable
private fun ChartFrame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SolColors.Panel)
            .padding(8.dp),
        content = content
    )
}

private val WeekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
private fun HistoryCalendar(
    month: YearMonth,
    weeks: List<com.brian.solwidget.data.CalendarWeek>,
    selectedWeek: LocalDate?,
    today: LocalDate,
    onShiftMonth: (Int) -> Unit,
    onSelectWeek: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SolColors.Panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { onShiftMonth(-1) },
                modifier = Modifier.semantics { contentDescription = "Previous month" }
            ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = MonthLabel.format(month),
                color = SolColors.Ink,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = { onShiftMonth(1) },
                modifier = Modifier.semantics { contentDescription = "Next month" }
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = SolColors.Muted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        weeks.forEach { week ->
            val selected = week.id == selectedWeek
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SolColors.TableStripe else SolColors.PanelAlt)
                    .then(
                        if (selected) {
                            Modifier.border(1.dp, SolColors.Forecast, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectWeek(week.id) }
                    .padding(vertical = 8.dp)
            ) {
                week.days.forEach { day ->
                    val color = when {
                        day.date == today -> SolColors.Forecast
                        !day.inMonth -> Color(0xFF5C6B82)
                        else -> SolColors.Ink
                    }
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        modifier = Modifier.weight(1f),
                        color = color,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
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
        LayerChip("Buy", layers.buy, Modifier.weight(1f)) { onToggle(ChartLayer.BUY, it) }
        LayerChip("Sell", layers.sell, Modifier.weight(1f)) { onToggle(ChartLayer.SELL, it) }
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
                    "– Glow below freeze" to SolColors.TempFreeze,
                    "– Glow above 90°F" to SolColors.TempHot
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
        if (layers.rates && (showOnly == null || showOnly == MetaPanel.RATES)) {
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
                val rateKeys = buildList {
                    if (layers.buy) add("▮ Buy" to SolColors.Buy)
                    if (layers.sell) add("▮ Sell" to SolColors.Sell)
                }
                if (rateKeys.isNotEmpty()) LegendKeys(type, *rateKeys.toTypedArray())
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


