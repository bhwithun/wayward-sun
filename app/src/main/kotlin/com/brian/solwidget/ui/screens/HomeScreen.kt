package com.brian.solwidget.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.solwidget.R
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.hvacSummary
import com.brian.solwidget.data.hvacSummaryLabel
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.ecobeeFillPresence
import com.brian.solwidget.data.hvacPresence
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brian.solwidget.util.AppUpdate
import com.brian.solwidget.util.Formatters
import com.brian.solwidget.util.IntentUtils
import com.brian.solwidget.util.ReleaseOffer
import com.brian.solwidget.viewmodel.HomeTab
import com.brian.solwidget.viewmodel.HomeViewModel
import com.brian.solwidget.viewmodel.isSolcastQuotaMessage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var updateNotice by remember { mutableStateOf<UpdateNotice?>(null) }
    var updateCheck by remember { mutableStateOf(0) }
    var readyOffer by remember { mutableStateOf<ReleaseOffer?>(null) }
    val versionName = remember(context) { AppUpdate.installedVersionName(context) }
    var expandedPanel by remember { mutableStateOf<MetaPanel?>(null) }
    val showLiveOverlay = state.tab == HomeTab.LIVE && expandedPanel != null
    val openDrawer = {
        scope.launch { drawerState.open() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            val activity = context as? Activity ?: return@LifecycleEventObserver
            viewModel.reloadGasRate()
            AppUpdate.refreshAsync(activity) { offer ->
                if (offer != null && AppUpdate.claimPrompt(activity, offer)) readyOffer = offer
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeToast()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = SolColors.Panel
            ) {
                LayerDrawerContent(
                    layers = state.layers,
                    versionName = versionName,
                    checkingUpdates = updateNotice is UpdateNotice.Checking,
                    onToggle = viewModel::setLayer,
                    onReset = viewModel::resetLayers,
                    onClose = { scope.launch { drawerState.close() } },
                    onWebApp = {
                        scope.launch { drawerState.close() }
                        IntentUtils.openUrl(context, "https://sun.brianandkathi.com")
                    },
                    onCheckUpdates = {
                        scope.launch { drawerState.close() }
                        val generation = updateCheck + 1
                        updateCheck = generation
                        updateNotice = UpdateNotice.Checking
                        val activity = context as? Activity
                        if (activity == null) {
                            updateNotice = UpdateNotice.Failed
                        } else {
                            AppUpdate.checkNow(activity) { result ->
                                if (updateCheck != generation) return@checkNow
                                updateNotice = when (result) {
                                    is AppUpdate.Check.Update -> UpdateNotice.Available(result.offer)
                                    AppUpdate.Check.Current -> UpdateNotice.Current
                                    AppUpdate.Check.Failed -> UpdateNotice.Failed
                                }
                            }
                        }
                    },
                    onSettings = {
                        scope.launch {
                            drawerState.close()
                            onOpenSettings()
                        }
                    }
                )
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { openDrawer() }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open navigation",
                            tint = SolColors.Ink
                        )
                    }
                },
                title = {
                    Text(
                        text = "Wayward Sun",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.tab == HomeTab.LIVE) {
                            LivePane(
                                snapshot = snapshot,
                                weather = state.weather,
                                livePast = state.livePast,
                                ecobee = state.liveEcobee,
                                layers = state.layers,
                                now = state.now,
                                zone = zone,
                                isLoading = state.isLoading,
                                onOpenLayers = { openDrawer() },
                                onOpenSettings = onOpenSettings,
                                expandedPanel = expandedPanel,
                                onToggleExpand = { panel ->
                                    expandedPanel = if (expandedPanel == panel) null else panel
                                },
                                gasDollarsPerHour = state.gasDollarsPerHour
                            )
                        } else {
                            HistoryPane(
                                month = state.historyMonth,
                                selectedWeek = state.selectedWeek,
                                selectedDay = state.selectedDay,
                                weekSelected = state.historyWeekSelected,
                                history = state.history,
                                historyKey = state.historyKey,
                                historyLoading = state.historyLoading,
                                historyError = state.historyError,
                                layers = state.layers,
                                now = state.now,
                                onOpenLayers = { openDrawer() },
                                onShiftMonth = viewModel::shiftHistoryMonth,
                                onShiftWeek = viewModel::shiftHistoryWeek,
                                onSelectWeek = viewModel::selectHistoryWeek,
                                onShiftDay = viewModel::shiftHistoryDay,
                                onSelectDay = viewModel::selectHistoryDay,
                                onShowToday = viewModel::showCurrentHistory,
                                gasDollarsPerHour = state.gasDollarsPerHour
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
    UpdateDialog(
        notice = updateNotice,
        onDismiss = {
            updateCheck += 1
            updateNotice = null
        },
        onInstall = { offer ->
            updateCheck += 1
            updateNotice = null
            (context as? Activity)?.let { AppUpdate.startInstall(it, offer) }
        },
    )
    readyOffer?.let { offer ->
        ReadyUpdateDialog(
            versionName = offer.versionName,
            onLater = {
                readyOffer = null
                AppUpdate.dismiss(context, offer.versionName)
            },
            onInstall = {
                readyOffer = null
                (context as? Activity)?.let { AppUpdate.startInstall(it, offer) }
            },
            onDismiss = { readyOffer = null },
        )
    }
    }
}

private sealed interface UpdateNotice {
    data object Checking : UpdateNotice
    data object Current : UpdateNotice
    data class Available(val offer: ReleaseOffer) : UpdateNotice
    data object Failed : UpdateNotice
}

@Composable
private fun UpdateDialog(
    notice: UpdateNotice?,
    onDismiss: () -> Unit,
    onInstall: (ReleaseOffer) -> Unit,
) {
    if (notice == null) return
    val message = when (notice) {
        UpdateNotice.Checking -> stringResource(R.string.update_checking)
        UpdateNotice.Current -> stringResource(R.string.update_current)
        is UpdateNotice.Available -> stringResource(R.string.update_available, notice.offer.versionName)
        UpdateNotice.Failed -> stringResource(R.string.update_failed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_for_updates)) },
        text = { Text(message) },
        confirmButton = {
            if (notice is UpdateNotice.Available) {
                TextButton(onClick = { onInstall(notice.offer) }) {
                    Text(stringResource(R.string.update_install))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ReadyUpdateDialog(
    versionName: String,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.update_ready, versionName)) },
        confirmButton = {
            TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        },
    )
}

@Composable
internal fun HomeTabs(
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
    ecobee: List<EcobeeSample>,
    layers: ChartLayers,
    now: Instant,
    zone: ZoneId,
    isLoading: Boolean,
    onOpenLayers: () -> Unit,
    onOpenSettings: () -> Unit,
    expandedPanel: MetaPanel?,
    onToggleExpand: (MetaPanel) -> Unit,
    gasDollarsPerHour: Double,
    modifier: Modifier = Modifier
) {
    if (snapshot == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) CircularProgressIndicator()
        }
        return
    }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                isDemo = snapshot.isDemo && livePast.isNullOrEmpty(),
                ecobee = ecobee
            )
        }
        val (tooWarm, tooCool) = ecobeeFillPresence(ecobee, rangeFrom, rangeTo, now)
        val runtime = hvacPresence(ecobee, rangeFrom, rangeTo, now)
        ChartLegend(
            layers = layers,
            onOpen = onOpenLayers,
            showTooWarm = tooWarm,
            showTooCool = tooCool,
            showHeatPump = runtime.heatPump,
            showGasAux = runtime.aux,
            showCoolRun = runtime.cool
        )
        if (layers.hvac) {
            Text(
                text = hvacSummaryLabel(hvacSummary(ecobee, rangeFrom, rangeTo, now), gasDollarsPerHour),
                color = SolColors.Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    }
}

@Composable
internal fun HistoryPane(
    month: YearMonth?,
    selectedWeek: LocalDate?,
    selectedDay: LocalDate?,
    weekSelected: Boolean,
    history: HistoryPage?,
    historyKey: String?,
    historyLoading: Boolean,
    historyError: String?,
    layers: ChartLayers,
    now: Instant,
    onOpenLayers: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onSelectWeek: (LocalDate) -> Unit,
    onShiftDay: (Int) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onShowToday: () -> Unit,
    gasDollarsPerHour: Double,
    modifier: Modifier = Modifier
) {
    val today = DetroitCalendar.today(now)
    val shownMonth = month ?: YearMonth.from(today)
    val weeks = DetroitCalendar.monthWeeks(shownMonth)
    val week = weeks.find { it.id == selectedWeek }
        ?: weeks.find { days -> days.days.any { it.date == today } }
        ?: weeks.firstOrNull()
    val (fetchFrom, fetchTo) = DetroitCalendar.monthFetchRange(weeks)
    val pageMatches = historyKey == historyFetchKey(fetchFrom, fetchTo)
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        historyError?.let { StatusBanner(it) }
        val shownDay = selectedDay ?: today
        val shownWeek = week?.id ?: DetroitCalendar.weekStart(shownDay)
        if (weekSelected || week != null) {
            val (from, to) = if (weekSelected) {
                DetroitCalendar.weekRange(shownWeek)
            } else {
                DetroitCalendar.dayRange(shownDay)
            }
            val chart = if (pageMatches && history != null) history.chartFor(from, to, now) else null
            Text(
                text = historyPeriodLabel(weekSelected, shownDay, shownWeek),
                color = SolColors.Ink,
                style = MaterialTheme.typography.titleMedium
            )
            ChartFrame(
                modifier = Modifier
                    .adjacentSwipe(if (weekSelected) onShiftWeek else onShiftDay)
                    .semantics {
                        contentDescription = if (weekSelected) {
                            "History week. Swipe left for the next week or right for the previous week."
                        } else {
                            "History day. Swipe left for the next day or right for the previous day."
                        }
                    }
            ) {
                if (chart != null) {
                    PowerChart(
                        points = chart.points,
                        now = now,
                        weather = chart.weather,
                        rangeFrom = from,
                        rangeTo = to,
                        layers = layers,
                        sunDays = chart.sunDays,
                        zone = DetroitCalendar.ZONE,
                        ecobee = chart.ecobee
                    )
                }
                if (chart == null && historyLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            val (tooWarm, tooCool) = if (chart != null) {
                ecobeeFillPresence(chart.ecobee, from, to, now)
            } else {
                false to false
            }
            val runtime = if (chart != null) hvacPresence(chart.ecobee, from, to, now) else null
            ChartLegend(
                layers = layers,
                onOpen = onOpenLayers,
                showTooWarm = tooWarm,
                showTooCool = tooCool,
                showHeatPump = runtime?.heatPump == true,
                showGasAux = runtime?.aux == true,
                showCoolRun = runtime?.cool == true
            )
            if (layers.hvac && chart != null) {
                Text(
                    text = hvacSummaryLabel(hvacSummary(chart.ecobee, from, to, now), gasDollarsPerHour),
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (pageMatches && history != null && chart != null &&
                chart.points.isEmpty() && chart.weather.isEmpty() && chart.ecobee.isEmpty() &&
                historyError == null
            ) {
                Text(
                    text = "No stored intervals in this range yet.",
                    color = SolColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            HistoryCalendar(
                month = shownMonth,
                weeks = weeks,
                selectedWeek = if (weekSelected) shownWeek else null,
                selectedDay = if (weekSelected) null else shownDay,
                today = today,
                onShiftMonth = onShiftMonth,
                onSelectWeek = onSelectWeek,
                onSelectDay = onSelectDay,
                onShowToday = onShowToday
            )
        }
    }
}

@Composable
private fun ChartFrame(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SolColors.Panel)
            .padding(8.dp),
        content = content
    )
}

/**
 * Finger moving left opens the next page. Finger moving right opens the previous.
 * Watches the initial pass so a sideways swipe is not also a week tap, and a vertical
 * drag still scrolls the page.
 */
private fun Modifier.adjacentSwipe(onAdjacent: (Int) -> Unit): Modifier =
    pointerInput(onAdjacent) {
        val distance = 36.dp.toPx()
        val speed = 900.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var horizontal = false
            var dragged = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (horizontal) change.consume()
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                tracker.addPosition(change.uptimeMillis, change.position)
                dragged = dx
                if (!horizontal) {
                    if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        horizontal = true
                        change.consume()
                    }
                }
            }
            if (!horizontal) return@awaitEachGesture
            val velocity = tracker.calculateVelocity().x
            val delta = when {
                dragged <= -distance -> 1
                dragged >= distance -> -1
                velocity <= -speed -> 1
                velocity >= speed -> -1
                else -> 0
            }
            if (delta != 0) onAdjacent(delta)
        }
    }

private val WeekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
private val PeriodDay = DateTimeFormatter.ofPattern("EEE, MMM d")
private val PeriodMonthDay = DateTimeFormatter.ofPattern("MMM d")

private fun historyPeriodLabel(weekSelected: Boolean, day: LocalDate, weekStart: LocalDate): String {
    if (!weekSelected) return PeriodDay.format(day)
    val end = weekStart.plusDays(6)
    return if (weekStart.month == end.month) {
        "${PeriodMonthDay.format(weekStart)} – ${end.dayOfMonth}"
    } else {
        "${PeriodMonthDay.format(weekStart)} – ${PeriodMonthDay.format(end)}"
    }
}

private val WeekNumberGold = Color(0xFFC9A24A)
private val WeekChipIdle = Color.White.copy(alpha = 0.06f)
private val WeekChipPressed = Color.White.copy(alpha = 0.14f)
private val WeekChipSelected = Color(0xFF3A5680)
private val WeekChipSelectedPressed = Color(0xFF4A6896)

@Composable
internal fun HistoryCalendar(
    month: YearMonth,
    weeks: List<com.brian.solwidget.data.CalendarWeek>,
    selectedWeek: LocalDate?,
    selectedDay: LocalDate?,
    today: LocalDate,
    onShiftMonth: (Int) -> Unit,
    onSelectWeek: (LocalDate) -> Unit,
    onSelectDay: ((LocalDate) -> Unit)?,
    onShowToday: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SolColors.Panel)
            .adjacentSwipe(onShiftMonth)
            .semantics {
                contentDescription =
                    "Month calendar. Swipe left for the next month or right for the previous month."
            }
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
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowToday)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Back to this week" }
            )
            TextButton(
                onClick = { onShiftMonth(1) },
                modifier = Modifier.semantics { contentDescription = "Next month" }
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp))
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
            val weekOn = week.id == selectedWeek
            val shape = RoundedCornerShape(8.dp)
            key(week.id) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (weekOn) SolColors.TableStripe else Color.Transparent)
                    .then(
                        if (weekOn) Modifier.border(1.dp, SolColors.Forecast, shape) else Modifier
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current
                        ) { onSelectWeek(week.id) }
                        .semantics { contentDescription = "Week ${DetroitCalendar.weekNumber(week.id)}" },
                    contentAlignment = Alignment.Center
                ) {
                    val number = DetroitCalendar.weekNumber(week.id)
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    weekOn && pressed -> WeekChipSelectedPressed
                                    weekOn -> WeekChipSelected
                                    pressed -> WeekChipPressed
                                    else -> WeekChipIdle
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "W$number",
                            color = if (weekOn) Color.White else WeekNumberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                week.days.forEach { day ->
                    val dayOn = !weekOn && day.date == selectedDay
                    val color = if (!day.inMonth) Color(0xFF5C6B82) else SolColors.Ink
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dayOn) SolColors.TableStripe else Color.Transparent)
                            .then(
                                if (dayOn) {
                                    Modifier.border(1.dp, SolColors.Forecast, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelectDay?.invoke(day.date) }
                            .semantics { contentDescription = "Day ${day.date}" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.date.dayOfMonth.toString(),
                            color = color,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        if (day.date == today) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SolColors.Forecast)
                            )
                        }
                    }
                }
            }
            }
        }
        }
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .padding(start = 43.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.10f))
            )
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
private fun LayerChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SolColors.TableStripe else SolColors.Panel)
            .clickable { onClick(!selected) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else SolColors.Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
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
        if (layers.power && (showOnly == null || showOnly == MetaPanel.SOLAR)) {
            MetaCard(
                title = "Solar",
                expanded = expanded,
                onClick = { onToggleExpand(MetaPanel.SOLAR) }
            ) {
                LegendKeys(type, "● Solcast" to SolColors.Forecast, "● Enphase" to SolColors.Live, "● Now" to SolColors.Now)
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
                    "– Outdoor °F" to SolColors.Temp,
                    "·· 32°F" to Color.White.copy(alpha = 0.25f)
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


