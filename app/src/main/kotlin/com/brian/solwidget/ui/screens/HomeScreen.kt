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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.solwidget.R
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.HouseDay
import com.brian.solwidget.data.HouseSample
import com.brian.solwidget.data.HouseSummary
import com.brian.solwidget.data.houseChipVisible
import com.brian.solwidget.data.houseSummary
import com.brian.solwidget.data.houseSummaryLabel
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.chartFor
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.components.PowerChart
import com.brian.solwidget.ui.theme.SolColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brian.solwidget.util.AppUpdate
import com.brian.solwidget.util.Formatters
import com.brian.solwidget.util.IntentUtils
import com.brian.solwidget.util.ReleaseOffer
import com.brian.solwidget.data.houseHalfHourPeak
import com.brian.solwidget.data.solarDisplayName
import com.brian.solwidget.viewmodel.HomeViewModel
import com.brian.solwidget.viewmodel.isSolcastQuotaMessage
import com.brian.solwidget.viewmodel.showTodayButton
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snapshot = state.snapshot
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var updateNotice by remember { mutableStateOf<UpdateNotice?>(null) }
    var updateCheck by remember { mutableStateOf(0) }
    var readyOffer by remember { mutableStateOf<ReleaseOffer?>(null) }
    val versionName = remember(context) { AppUpdate.installedVersionName(context) }
    var showHouseChip by remember { mutableStateOf(false) }
    var periodReadout by remember { mutableStateOf(PeriodReadout()) }
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

    val expanded = state.chartExpanded
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    var layersOpen by rememberSaveable { mutableStateOf(false) }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var expandHintSeen by remember { mutableStateOf(true) }
    val expandProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(CHART_EXPAND_MILLIS, easing = FastOutSlowInEasing),
        label = "chartExpand"
    )
    val covering = expanded || expandProgress > 0.01f
    val showExpandHint = showChartExpandHint(expandHintSeen, expanded)
    LaunchedEffect(Unit) {
        expandHintSeen = AppStorage(context).chartExpandHintSeen()
    }
    LaunchedEffect(showExpandHint) {
        if (!showExpandHint) return@LaunchedEffect
        delay(2400)
        AppStorage(context).setChartExpandHintSeen()
        expandHintSeen = true
    }
    DisposableEffect(expanded) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else {
            null
        }
        if (expanded && window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else if (window != null && controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
        onDispose {
            if (window != null && controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen && !expanded && !legendOpen && !layersOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = legendOpen && !layersOpen) { legendOpen = false }
    BackHandler(enabled = layersOpen) { layersOpen = false }
    BackHandler(enabled = expanded && !legendOpen && !layersOpen) { viewModel.setChartExpanded(false) }
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
                    showHouse = showHouseChip,
                    widgetHouse = state.widgetHouse,
                    onWidgetHouse = viewModel::setWidgetHouse,
                    details = {
                        ChartDetails(
                            layers = state.layers,
                            snapshot = snapshot,
                            weather = state.weather,
                            now = state.now,
                            readout = periodReadout,
                            onOpenSettings = {
                                scope.launch {
                                    drawerState.close()
                                    onOpenSettings()
                                }
                            }
                        )
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
    val chartControls = ChartControls(
        expanded = covering,
        expandProgress = expandProgress,
        cardBounds = cardBounds,
        onChartTap = {
            legendOpen = false
            layersOpen = false
            viewModel.setChartExpanded(!expanded)
        },
        onSwipe = { delta ->
            legendOpen = false
            layersOpen = false
            if (state.historyWeekSelected) {
                viewModel.shiftHistoryWeek(delta)
            } else {
                viewModel.shiftHistoryDay(delta)
            }
        },
        onCardBounds = { cardBounds = it },
        legendOpen = legendOpen,
        onToggleLegend = {
            layersOpen = false
            legendOpen = !legendOpen
        },
        onDismissLegend = { legendOpen = false },
        layersOpen = layersOpen,
        onToggleLayers = {
            legendOpen = false
            layersOpen = !layersOpen
        },
        onDismissLayers = { layersOpen = false },
        onToggleLayer = viewModel::setLayer,
        onResetLayers = viewModel::resetLayers,
        showHouse = showHouseChip,
        showExpandHint = showExpandHint && !covering
    )
    HomeScaffold(
        covering = covering,
        legendOpen = legendOpen,
        onOpenDrawer = { openDrawer() },
        onDismissLegend = { legendOpen = false }
    ) {
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
            onShiftMonth = viewModel::shiftHistoryMonth,
            onShiftWeek = viewModel::shiftHistoryWeek,
            onSelectWeek = viewModel::selectHistoryWeek,
            onShiftDay = viewModel::shiftHistoryDay,
            onSelectDay = viewModel::selectHistoryDay,
            onShowToday = viewModel::showCurrentHistory,
            onShowTodayDay = viewModel::showToday,
            gasDollarsPerHour = state.gasDollarsPerHour,
            onHouseChip = { showHouseChip = it },
            onPeriodReadout = { periodReadout = it },
            notice = snapshot?.errorMessage?.takeUnless { it.isSolcastQuotaMessage() },
            demo = snapshot?.isDemo == true && snapshot.errorMessage == null,
            controls = chartControls
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScaffold(
    covering: Boolean,
    legendOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDismissLegend: () -> Unit,
    pane: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            if (!covering) {
                Box {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onOpenDrawer) {
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
                    if (legendOpen) {
                        LegendScrim(onDismissLegend)
                    }
                }
            }
        },
        containerColor = SolColors.Navy
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (covering) PaddingValues(0.dp) else padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (covering) 0.dp else 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("home-body")
                ) {
                    pane()
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
    onShiftMonth: (Int) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onSelectWeek: (LocalDate) -> Unit,
    onShiftDay: (Int) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onShowToday: () -> Unit,
    onShowTodayDay: () -> Unit = {},
    gasDollarsPerHour: Double,
    onHouseChip: (Boolean) -> Unit = {},
    onPeriodReadout: (PeriodReadout) -> Unit = {},
    notice: String? = null,
    demo: Boolean = false,
    modifier: Modifier = Modifier,
    controls: ChartControls = ChartControls()
) {
    val today = DetroitCalendar.today(now)
    val shownMonth = month ?: YearMonth.from(today)
    val weeks = DetroitCalendar.monthWeeks(shownMonth)
    val week = weeks.find { it.id == selectedWeek }
        ?: weeks.find { days -> days.days.any { it.date == today } }
        ?: weeks.firstOrNull()
    val (fetchFrom, fetchTo) = DetroitCalendar.monthFetchRange(weeks)
    val pageMatches = historyKey == historyFetchKey(fetchFrom, fetchTo)
    val shownDay = selectedDay ?: today
    val shownWeek = week?.id ?: DetroitCalendar.weekStart(shownDay)
    val (from, to) = if (weekSelected) {
        DetroitCalendar.weekRange(shownWeek)
    } else {
        DetroitCalendar.dayRange(shownDay)
    }
    val chart = if (pageMatches && history != null) history.chartFor(from, to, now) else null
    val houseDays = if (pageMatches) history?.houseDays.orEmpty() else emptyList()
    val houseVisible = houseChipVisible(chart?.house.orEmpty(), houseDays, from, to, DetroitCalendar.ZONE)
    SideEffect { onHouseChip(houseVisible) }
    val houseLine = if (layers.house && chart != null) {
        houseSummary(chart.house, houseDays, from, to, DetroitCalendar.ZONE, now, layers.power)
    } else {
        null
    }
    val (tooWarm, tooCool) = if (chart != null) {
        ecobeeFillPresence(chart.ecobee, from, to, now)
    } else {
        false to false
    }
    val runtime = if (chart != null) hvacPresence(chart.ecobee, from, to, now) else null
    val legend = LegendModel(
        layers = layers,
        showTooWarm = tooWarm,
        showTooCool = tooCool,
        showHeatPump = runtime?.heatPump == true,
        showGasAux = runtime?.aux == true,
        showCoolRun = runtime?.cool == true,
        showHouse = layers.house && houseVisible,
        housePeakKw = if (chart == null) {
            null
        } else {
            houseHalfHourPeak(chart.house, from, to)
        }
    )
    val chartContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
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
                ecobee = chart.ecobee,
                house = chart.house
            )
        }
        if (chart == null && historyLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
    val zoneDate = DetroitCalendar.ZONE
    val windowFrom = history?.from?.atZone(zoneDate)?.toLocalDate()
    val windowTo = history?.to?.atZone(zoneDate)?.toLocalDate()
    val sampleDates = buildList {
        history?.points?.forEach { add(it.periodEnd.atZone(zoneDate).toLocalDate()) }
        history?.ecobee?.forEach { add(it.at.atZone(zoneDate).toLocalDate()) }
    }
    val earliest = sampleDates.minOrNull()
    val anchor = if (weekSelected) shownWeek else shownDay
    fun canStep(delta: Int) = if (weekSelected) {
        DetroitCalendar.canStepWeek(anchor, delta, today, windowFrom, windowTo, earliest)
    } else {
        DetroitCalendar.canStepDay(anchor, delta, today, windowFrom, windowTo, earliest)
    }
    fun pageFor(start: LocalDate): @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        val (pageFrom, pageTo) = if (weekSelected) DetroitCalendar.weekRange(start) else DetroitCalendar.dayRange(start)
        val drawn = if (pageMatches && history != null) history.chartFor(pageFrom, pageTo, now) else null
        if (drawn != null) {
            val pageSummary = if (layers.house) {
                houseSummary(
                    drawn.house,
                    history?.houseDays.orEmpty(),
                    pageFrom,
                    pageTo,
                    DetroitCalendar.ZONE,
                    now,
                    layers.power
                )
            } else {
                null
            }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PowerChart(
                        points = drawn.points,
                        now = now,
                        weather = drawn.weather,
                        rangeFrom = pageFrom,
                        rangeTo = pageTo,
                        layers = layers,
                        sunDays = drawn.sunDays,
                        zone = DetroitCalendar.ZONE,
                        ecobee = drawn.ecobee,
                        house = drawn.house
                    )
                }
                HouseSummaryRow(pageSummary)
            }
        }
    }
    val anchors = rememberStableSpan(
        weekSelected,
        if (weekSelected) {
            DetroitCalendar.fullscreenWeeks(shownWeek, today, windowFrom, windowTo, earliest)
        } else {
            DetroitCalendar.fullscreenDays(shownDay, today, windowFrom, windowTo, earliest)
        }
    )
    val pages = anchors.map { start -> FullscreenChartPage(start, pageFor(start)) }
    val hvacLabel = if (chart != null) {
        hvacSummaryLabel(hvacSummary(chart.ecobee, from, to, now), gasDollarsPerHour)
    } else {
        ""
    }
    val readout = periodReadout(layers.hvac && chart != null, hvacLabel, houseLine)
    LaunchedEffect(readout) { onPeriodReadout(readout) }
    if (controls.expanded) {
        ExpandedChart(
            modifier = modifier,
            controls = controls,
            legend = legend,
            pages = pages,
            startIndex = anchors.indexOf(anchor).coerceAtLeast(0),
            pagerKey = weekSelected,
            onSettled = { index ->
                val target = anchors.getOrNull(index) ?: return@ExpandedChart
                if (weekSelected) {
                    val steps = ((target.toEpochDay() - shownWeek.toEpochDay()) / 7).toInt()
                    if (steps != 0) {
                        val step = if (steps > 0) 1 else -1
                        repeat(abs(steps).coerceAtMost(12)) { onShiftWeek(step) }
                    }
                } else {
                    val steps = (target.toEpochDay() - shownDay.toEpochDay()).toInt()
                    if (steps != 0) {
                        val step = if (steps > 0) 1 else -1
                        repeat(abs(steps).coerceAtMost(31)) { onShiftDay(step) }
                    }
                }
            },
            showDots = false,
            title = historyPeriodLabel(weekSelected, shownDay, shownWeek),
            showToday = showTodayButton(weekSelected, selectedDay, today),
            onToday = onShowTodayDay
        )
        return
    }
    Box(modifier.fillMaxSize().testTag("history-pane")) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("history-chart-half"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            notice?.let { StatusBanner(it) }
            if (demo) {
                StatusBanner("Showing sample output until the shared cache has Solcast data.")
            }
            historyError?.let { StatusBanner(it) }
            if (weekSelected || week != null) {
                ChartHeading(
                    historyPeriodLabel(weekSelected, shownDay, shownWeek),
                    controls,
                    showToday = showTodayButton(weekSelected, selectedDay, today),
                    onToday = onShowTodayDay
                )
                if (controls.showExpandHint) {
                    Text("Tap chart to expand", color = SolColors.Muted, fontSize = 12.sp)
                }
                ChartFrame(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("history-chart")
                        .adjacentSwipe { delta ->
                            if (!canStep(delta)) return@adjacentSwipe
                            if (weekSelected) onShiftWeek(delta) else onShiftDay(delta)
                        }
                        .semantics {
                            contentDescription = if (weekSelected) {
                                "History week. Swipe left for the next week or right for the previous week."
                            } else {
                                "History day. Swipe left for the next day or right for the previous day."
                            }
                        },
                    fillHeight = true,
                    onTap = controls.onChartTap,
                    onBounds = controls.onCardBounds,
                    content = chartContent
                )
                if (pageMatches && history != null && chart != null &&
                    chart.points.isEmpty() && chart.weather.isEmpty() && chart.ecobee.isEmpty() &&
                    !houseVisible &&
                    historyError == null
                ) {
                    Text(
                        text = "No stored intervals in this range yet.",
                        color = SolColors.Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        HistoryCalendar(
            month = shownMonth,
            weeks = weeks,
            selectedWeek = if (weekSelected) shownWeek else null,
            selectedDay = if (weekSelected) null else shownDay,
            today = today,
            onShiftMonth = onShiftMonth,
            onSelectWeek = onSelectWeek,
            onSelectDay = onSelectDay,
            onShowToday = onShowToday,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
    if (controls.legendOpen) {
        LegendScrim(controls.onDismissLegend)
        LegendCard(
            legend,
            Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 12.dp).zIndex(2f)
        )
    }
    }
}

@Composable
private fun HouseSummaryBlock(summary: HouseSummary?) {
    if (summary == null) return
    Text(
        text = houseSummaryLabel(summary),
        color = SolColors.Muted,
        style = MaterialTheme.typography.bodySmall
    )
    if (summary.hourlyDetailUnavailable) {
        Text(
            text = "Hourly detail unavailable",
            color = SolColors.Muted.copy(alpha = 0.72f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun HouseSummaryRow(summary: HouseSummary?) {
    if (summary == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("fullscreen-house-summary")
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
    ) {
        HouseSummaryBlock(summary)
    }
}

private val DateRowHeight = 48.dp

@Composable
private fun ChartHeading(
    title: String,
    controls: ChartControls,
    showToday: Boolean,
    onToday: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DateRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = SolColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showToday) {
            TodayButton(onClick = onToday)
        }
        LegendButton(
            open = controls.legendOpen,
            onClick = controls.onToggleLegend
        )
    }
}

/**
 * Covers the surrounding [Box] only. [Modifier.matchParentSize] does not
 * participate in measurement, so the top bar stays its own height.
 * [Modifier.fillMaxSize] in that unsized box takes the screen max and
 * collapses the chart body to zero.
 */
@Composable
private fun BoxScope.LegendScrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .zIndex(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .semantics { contentDescription = "Dismiss legend" }
    )
}

/**
 * Keeps every period already opened, through the latest one. Moving into a later
 * month must not drop the earlier pages and shift the pager's index.
 */
@Composable
private fun rememberStableSpan(weekMode: Boolean, computed: List<LocalDate>): List<LocalDate> {
    var earliest by remember(weekMode) { mutableStateOf<LocalDate?>(null) }
    var latest by remember(weekMode) { mutableStateOf<LocalDate?>(null) }
    val start = listOfNotNull(earliest, computed.firstOrNull()).minOrNull() ?: return computed
    val end = listOfNotNull(latest, computed.lastOrNull()).maxOrNull() ?: return computed
    SideEffect {
        if (start != earliest) earliest = start
        if (end != latest) latest = end
    }
    val step = if (weekMode) 7L else 1L
    val pages = ArrayList<LocalDate>()
    var cursor = start
    var guard = 0
    while (!cursor.isAfter(end) && guard++ < 2000) {
        pages += cursor
        cursor = cursor.plusDays(step)
    }
    return pages
}

internal class FullscreenChartPage(
    val key: Any,
    val content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
)

@Composable
private fun ExpandedChart(
    modifier: Modifier,
    controls: ChartControls,
    legend: LegendModel,
    pages: List<FullscreenChartPage>,
    startIndex: Int,
    pagerKey: Any,
    onSettled: (Int) -> Unit,
    showDots: Boolean,
    title: String,
    showToday: Boolean,
    onToday: () -> Unit
) {
    BackHandler(enabled = controls.layersOpen) { controls.onDismissLayers() }
    if (pages.isEmpty()) return
    val sheetProgress by animateFloatAsState(
        targetValue = if (controls.layersOpen) 1f else 0f,
        animationSpec = tween(200),
        label = "layerSheet"
    )
    val layersCovering = controls.layersOpen || sheetProgress > 0.01f
    val chartGestures = fullscreenChartGesturesEnabled(controls.legendOpen, layersCovering)
    val safeStart = startIndex.coerceIn(0, pages.lastIndex)
    val pagerState = key(pagerKey) {
        rememberPagerState(initialPage = safeStart) { pages.size }
    }
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = PAGER_POSITION_THRESHOLD
    )
    LaunchedEffect(pagerState.settledPage) {
        onSettled(pagerState.settledPage)
    }
    // A loaded month can drop earlier pages. Keep the settled date on its own page.
    LaunchedEffect(pages.map { it.key }, safeStart) {
        val anchorKey = pages.getOrNull(safeStart)?.key ?: return@LaunchedEffect
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        if (pages.getOrNull(pagerState.settledPage)?.key != anchorKey) {
            pagerState.scrollToPage(safeStart)
        }
    }
    if (controls.previewDragFraction != 0f) {
        SideEffect {
            pagerState.requestScrollToPage(
                pagerState.settledPage.coerceIn(0, pages.lastIndex),
                -controls.previewDragFraction.coerceIn(-0.5f, 0.5f)
            )
        }
    }
    var full by remember { mutableStateOf<Rect?>(null) }
    Box(
        modifier
            .fillMaxSize()
            .testTag("fullscreen-chart")
            .background(SolColors.Navy)
            .onGloballyPositioned { full = it.boundsInRoot() }
            .scaleFromCard(controls.expandProgress, controls.cardBounds, full)
            .padding(fullscreenSafePadding())
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(FullscreenHeaderHeight)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(FullscreenLayersButtonSize))
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    color = SolColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showToday) {
                    TodayButton(onClick = onToday)
                }
                if (showDots && pages.size in 2..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(pages.size) { index ->
                            val selected = index == pagerState.currentPage
                            Box(
                                Modifier
                                    .size(if (selected) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (selected) 0.9f else 0.35f))
                            )
                        }
                    }
                }
                LegendButton(
                    open = controls.legendOpen,
                    onClick = controls.onToggleLegend,
                    compact = true,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        top = fullscreenPlotInset(
                            FullscreenLayersButtonSize,
                            FullscreenHeaderHeight,
                            FullscreenPlotHeadroom
                        )
                    )
                    .clipToBounds()
                    .testTag("fullscreen-plot")
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("fullscreen-pager")
                        .background(SolColors.Panel)
                        .fullscreenChartTap(
                            enabled = chartGestures,
                            onTap = controls.onChartTap
                        ),
                    beyondViewportPageCount = 1,
                    flingBehavior = fling,
                    userScrollEnabled = chartGestures,
                    key = { index -> pages.getOrNull(index)?.key ?: index }
                ) { index ->
                    val page = pages.getOrNull(index) ?: return@HorizontalPager
                    Box(
                        Modifier.fillMaxSize().background(SolColors.Panel),
                        content = page.content
                    )
                }
                if (pagerState.canScrollBackward) {
                    PagerChevron(pointsLeft = true, Modifier.align(Alignment.CenterStart))
                }
                if (pagerState.canScrollForward) {
                    PagerChevron(pointsLeft = false, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
        if (controls.legendOpen) {
            LegendScrim(controls.onDismissLegend)
            LegendCard(
                legend,
                Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 12.dp).zIndex(2f)
            )
        }
        FullscreenLayersButton(
            onClick = controls.onToggleLayers,
            modifier = Modifier.align(Alignment.TopStart).zIndex(2.5f)
        )
        if (layersCovering) {
            FullscreenLayerSheet(
                progress = sheetProgress,
                layers = legend.layers,
                showHouse = controls.showHouse,
                onToggle = controls.onToggleLayer,
                onReset = controls.onResetLayers,
                onDismiss = controls.onDismissLayers
            )
        }
    }
}

/**
 * Narrow sheet of the same layer switches as the drawer. It slides in from the
 * start edge. Taps on the sheet and on the scrim are consumed here, so they
 * cannot also leave fullscreen.
 */
@Composable
private fun BoxScope.FullscreenLayerSheet(
    progress: Float,
    layers: ChartLayers,
    showHouse: Boolean,
    onToggle: (ChartLayer, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    BoxWithConstraints(Modifier.matchParentSize().zIndex(3f)) {
        val sheetWidth = fullscreenLayerSheetWidth(maxWidth)
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = FullscreenLayerScrimAlpha * progress.coerceIn(0f, 1f)))
                .tapOutsideToDismiss(onDismiss)
                .testTag("fullscreen-layers-scrim")
                .semantics {
                    role = Role.Button
                    contentDescription = "Dismiss layers"
                    onClick {
                        onDismiss()
                        true
                    }
                }
        )
        val density = LocalDensity.current
        val travel = with(density) { sheetWidth.toPx() }
        val offsetX = ((1f - progress.coerceIn(0f, 1f)) * -travel).roundToInt()
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetX, 0) }
                .width(sheetWidth)
                .fillMaxHeight()
                .background(SolColors.Panel)
                .testTag("fullscreen-layers-sheet")
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            ) {
                LayerSwitchSection(
                    layers = layers,
                    onToggle = onToggle,
                    onReset = onReset,
                    onClose = onDismiss,
                    includeWidgetRows = false,
                    showHouse = showHouse
                )
            }
        }
    }
}

/**
 * A tap dismisses. A drag is swallowed so it cannot turn the page or leave fullscreen.
 * The pointer stays on this node, which sits above the chart.
 */
private fun Modifier.tapOutsideToDismiss(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    val slop = viewConfiguration.touchSlop
    val longPress = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val origin = down.position
        val start = down.uptimeMillis
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            change.consume()
            if (!change.pressed) {
                if (!moved && change.uptimeMillis - start < longPress) onTap()
                return@awaitEachGesture
            }
            val dx = change.position.x - origin.x
            val dy = change.position.y - origin.y
            if (abs(dx) > slop || abs(dy) > slop) moved = true
        }
    }
}

@Composable
private fun PagerChevron(pointsLeft: Boolean, modifier: Modifier) {
    Text(
        text = if (pointsLeft) "‹" else "›",
        color = Color.White.copy(alpha = 0.30f),
        fontSize = 28.sp,
        modifier = modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun fullscreenSafePadding(): androidx.compose.foundation.layout.PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val gestures = WindowInsets.systemGestures
    val mandatory = WindowInsets.mandatorySystemGestures
    val edges = fullscreenSafePaddingPx(
        cutoutStart = cutout.getLeft(density, layoutDirection),
        cutoutTop = cutout.getTop(density),
        cutoutEnd = cutout.getRight(density, layoutDirection),
        cutoutBottom = cutout.getBottom(density),
        gestureTop = gestures.getTop(density),
        gestureBottom = gestures.getBottom(density),
        mandatoryTop = mandatory.getTop(density),
        mandatoryBottom = mandatory.getBottom(density)
    )
    return with(density) {
        androidx.compose.foundation.layout.PaddingValues(
            start = edges.start.toDp(),
            top = edges.top.toDp(),
            end = edges.end.toDp(),
            bottom = edges.bottom.toDp()
        )
    }
}

internal data class ChartControls(
    val expanded: Boolean = false,
    val expandProgress: Float = 1f,
    val cardBounds: Rect? = null,
    val onChartTap: () -> Unit = {},
    val onSwipe: (Int) -> Unit = {},
    val pageIndex: Int = 0,
    val previewDragFraction: Float = 0f,
    val onCardBounds: (Rect) -> Unit = {},
    val legendOpen: Boolean = false,
    val onToggleLegend: () -> Unit = {},
    val onDismissLegend: () -> Unit = {},
    val layersOpen: Boolean = false,
    val onToggleLayers: () -> Unit = {},
    val onDismissLayers: () -> Unit = {},
    val onToggleLayer: (ChartLayer, Boolean) -> Unit = { _, _ -> },
    val onResetLayers: () -> Unit = {},
    val showHouse: Boolean = false,
    val showExpandHint: Boolean = false
)

@Composable
private fun ChartFrame(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    fillHeight: Boolean = false,
    onTap: (() -> Unit)? = null,
    onBounds: ((Rect) -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .then(
                when {
                    expanded || fillHeight -> Modifier
                    else -> Modifier.fillMaxWidth().height(300.dp)
                }
            )
            .onGloballyPositioned { coords -> onBounds?.invoke(coords.boundsInRoot()) }
            .then(if (expanded) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
            .background(SolColors.Panel)
            .padding(if (expanded) 0.dp else 8.dp)
            .then(if (onTap != null) Modifier.chartTap(onTap) else Modifier),
        content = content
    )
}

/**
 * Finger moving left opens the next page. Finger moving right opens the previous.
 * Watches the initial pass so a sideways swipe is not also a week tap, and a vertical
 * drag still scrolls the page. The callback stays current without restarting the gesture.
 */
@Composable
private fun Modifier.adjacentSwipe(onAdjacent: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(onAdjacent)
    return pointerInput(Unit) {
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
            if (delta != 0) current(delta)
        }
    }
}

private val WeekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
private val PeriodDay = DateTimeFormatter.ofPattern("EEE, MMM d")
private val PeriodMonthDay = DateTimeFormatter.ofPattern("MMM d")

private fun historyPeriodLabel(weekSelected: Boolean, day: LocalDate, weekStart: LocalDate): String {
    if (!weekSelected) return PeriodDay.format(day)
    return periodRangeLabel(weekStart, weekStart.plusDays(6))
}

/** Inclusive local dates, matching the History week header across month boundaries. */
internal fun periodRangeLabel(start: LocalDate, end: LocalDate): String {
    return if (start.year == end.year && start.month == end.month) {
        "${PeriodMonthDay.format(start)} – ${end.dayOfMonth}"
    } else {
        "${PeriodMonthDay.format(start)} – ${PeriodMonthDay.format(end)}"
    }
}

internal fun livePeriodLabel(now: Instant, zone: ZoneId): String {
    val (from, to) = ForecastSnapshot.range(now, zone)
    val start = from.atZone(zone).toLocalDate()
    val end = to.atZone(zone).toLocalDate().minusDays(1)
    return periodRangeLabel(start, end)
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
    onShowToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forwardLimit = DetroitCalendar.forwardLimit(today)
    val lastWeek = DetroitCalendar.weekStart(forwardLimit)
    fun shiftMonth(delta: Int) {
        if (DetroitCalendar.canShiftMonth(month, delta, today)) onShiftMonth(delta)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .testTag("history-calendar")
            .clip(RoundedCornerShape(14.dp))
            .background(SolColors.Panel)
            .adjacentSwipe { shiftMonth(it) }
            .semantics {
                contentDescription =
                    "Month calendar. Swipe left for the next month or right for the previous month."
            }
    ) {
        val finite = maxHeight.value.isFinite()
        val metrics = if (finite && weeks.isNotEmpty()) {
            CalendarFit.layout(maxHeight, weeks.size)
        } else {
            CalendarFit.preferred(weeks.size)
        }
        val cell = metrics.cell
        val dayFont = metrics.daySp.sp
        val titleFont = if (metrics.header < CalendarFit.headerRoomy) 14.sp else 16.sp
        val chevronFont = if (metrics.header < CalendarFit.headerRoomy) 18.sp else 22.sp
        val dot = if (cell < CalendarFit.tightenBelow) 3.dp else 4.dp
        val chipHeight = minOf(26.dp, (cell - 2.dp).coerceAtLeast(0.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (finite) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = 8.dp, vertical = metrics.verticalPadding / 2)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.header),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthChevron("‹", "Previous month", chevronFont, enabled = true) { shiftMonth(-1) }
                Text(
                    text = MonthLabel.format(month),
                    color = SolColors.Ink,
                    fontSize = titleFont,
                    lineHeight = titleFont,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowToday)
                        .semantics { contentDescription = "Back to this week" }
                )
                MonthChevron(
                    "›",
                    "Next month",
                    chevronFont,
                    enabled = DetroitCalendar.canShiftMonth(month, 1, today)
                ) { shiftMonth(1) }
            }
            Spacer(Modifier.height(metrics.gap))
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(metrics.weekday),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(44.dp))
                        WeekdayLabels.forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                color = SolColors.Muted,
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                    weeks.forEach { week ->
                        val weekOn = week.id == selectedWeek
                        val shape = RoundedCornerShape(8.dp)
                        Spacer(Modifier.height(metrics.gap))
                        key(week.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cell)
                                .clip(shape)
                                .background(if (weekOn) SolColors.TableStripe else Color.Transparent)
                                .then(
                                    if (weekOn) Modifier.border(1.dp, SolColors.Forecast, shape) else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = LocalIndication.current,
                                        enabled = !week.id.isAfter(lastWeek)
                                    ) { onSelectWeek(week.id) }
                                    .semantics { contentDescription = "Week ${DetroitCalendar.weekNumber(week.id)}" },
                                contentAlignment = Alignment.Center
                            ) {
                                val number = DetroitCalendar.weekNumber(week.id)
                                Box(
                                    modifier = Modifier
                                        .width(if (cell < 32.dp) 28.dp else 32.dp)
                                        .height(chipHeight)
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
                                        fontSize = if (cell < 32.dp) 10.sp else 12.sp,
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
                                        .fillMaxHeight()
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
                                        .clickable(enabled = onSelectDay != null && !day.date.isAfter(forwardLimit)) {
                                            onSelectDay?.invoke(day.date)
                                        }
                                        .semantics { contentDescription = "Day ${day.date}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day.date.dayOfMonth.toString(),
                                            color = color,
                                            textAlign = TextAlign.Center,
                                            fontSize = dayFont,
                                            lineHeight = dayFont,
                                            maxLines = 1
                                        )
                                        if (day.date == today) {
                                            Spacer(Modifier.height(1.dp))
                                            Box(
                                                Modifier
                                                    .size(dot)
                                                    .clip(RoundedCornerShape(dot / 2))
                                                    .background(SolColors.Forecast)
                                            )
                                        }
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
    }
}

@Composable
private fun MonthChevron(
    label: String,
    description: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) SolColors.Forecast else SolColors.Muted,
            fontSize = fontSize,
            lineHeight = fontSize
        )
    }
}

internal enum class MetaPanel { SOLAR, TEMP, PRECIP, RATES }

/** HVAC hours and house use for the open chart. The day view no longer prints these under the chart. */
internal data class PeriodReadout(
    val hvac: String? = null,
    val house: String? = null,
    val hourlyDetailUnavailable: Boolean = false
)

internal fun periodReadout(
    hvacEnabled: Boolean,
    hvacLabel: String,
    house: HouseSummary?
): PeriodReadout = PeriodReadout(
    hvac = hvacLabel.trim().takeIf { hvacEnabled && it.isNotEmpty() },
    house = house?.let { houseSummaryLabel(it) },
    hourlyDetailUnavailable = house?.hourlyDetailUnavailable == true
)

@Composable
internal fun ChartDetails(
    layers: ChartLayers,
    snapshot: ForecastSnapshot?,
    weather: WeatherSnapshot?,
    now: Instant,
    readout: PeriodReadout,
    onOpenSettings: () -> Unit
) {
    val cards = snapshot != null && (layers.power || layers.temperature || layers.precipitation || layers.rates)
    val lines = readout.hvac != null || readout.house != null
    if (!cards && !lines) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart-details")
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "DETAILS",
            color = SolColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        readout.hvac?.let { line ->
            Text(line, color = SolColors.Ink, style = MaterialTheme.typography.bodyMedium)
        }
        readout.house?.let { line ->
            Text(line, color = SolColors.Ink, style = MaterialTheme.typography.bodyMedium)
            if (readout.hourlyDetailUnavailable) {
                Text(
                    text = "Hourly detail unavailable",
                    color = SolColors.Muted,
                    fontSize = 12.sp
                )
            }
        }
        if (snapshot != null && cards) {
            LayerMetaPanel(
                layers = layers,
                snapshot = snapshot,
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings,
                expandedPanel = null,
                onToggleExpand = {},
                onDrawer = true,
                clicks = false
            )
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
private fun LayerMetaPanel(
    layers: ChartLayers,
    snapshot: ForecastSnapshot,
    weather: WeatherSnapshot?,
    now: Instant,
    onOpenSettings: () -> Unit,
    expandedPanel: MetaPanel?,
    onToggleExpand: (MetaPanel) -> Unit,
    showOnly: MetaPanel? = null,
    onDrawer: Boolean = false,
    clicks: Boolean = true
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
                onDrawer = onDrawer,
                onClick = if (clicks) ({ onToggleExpand(MetaPanel.SOLAR) }) else null
            ) {
                if (snapshot.isDemo) {
                    Text(
                        text = "demo",
                        color = SolColors.Muted,
                        style = type.caption,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SolColors.PanelAlt)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                } else {
                    solarDisplayName(snapshot.resourceId)?.let { name ->
                        Text(name, color = SolColors.Ink, style = type.body)
                    }
                }
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
                onClick = if (clicks) ({ onToggleExpand(MetaPanel.TEMP) }) else null,
                onDrawer = onDrawer,
                type = type
            ) {}
        }
        if (layers.precipitation && (showOnly == null || showOnly == MetaPanel.PRECIP)) {
            WeatherMetaCard(
                title = "Precipitation",
                weather = weather,
                now = now,
                onOpenSettings = onOpenSettings,
                expanded = expanded,
                onClick = if (clicks) ({ onToggleExpand(MetaPanel.PRECIP) }) else null,
                onDrawer = onDrawer,
                type = type
            ) {
                LegendKeys(type, "▮ Precip %" to SolColors.Precip.copy(alpha = 1f))
            }
        }
        if (layers.rates && (showOnly == null || showOnly == MetaPanel.RATES)) {
            MetaCard(
                title = "Rates",
                expanded = expanded,
                onDrawer = onDrawer,
                onClick = if (clicks) ({ onToggleExpand(MetaPanel.RATES) }) else null
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
                RateTable(type, expanded, onDrawer)
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
    onClick: (() -> Unit)?,
    onDrawer: Boolean = false,
    type: MetaType,
    legend: @Composable () -> Unit
) {
    MetaCard(title = title, expanded = expanded, onClick = onClick, onDrawer = onDrawer) {
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
    onClick: (() -> Unit)?,
    onDrawer: Boolean = false,
    content: @Composable () -> Unit
) {
    val type = metaType(expanded)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxSize() else Modifier)
            .clip(RoundedCornerShape(if (expanded) 20.dp else 14.dp))
            .background(if (onDrawer) SolColors.PanelAlt else SolColors.Panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
private fun RateTable(type: MetaType, expanded: Boolean, onDrawer: Boolean = false) {
    val headerStyle = type.caption.copy(fontWeight = FontWeight.SemiBold)
    val cellStyle = type.body
    val rowPad = if (expanded) 14.dp else 6.dp
    val stripe = if (expanded || onDrawer) SolColors.TableStripe else SolColors.PanelAlt
    val even = when {
        expanded -> SolColors.PanelAlt
        onDrawer -> SolColors.Panel
        else -> Color.Transparent
    }
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun widthOf(text: String, style: TextStyle) = with(density) {
            measurer.measure(AnnotatedString(text), style = style).size.width.toDp().value
        }
        val plan = rateTablePlan(
            maxWidth.value,
            headerWidth = { widthOf(it, headerStyle) },
            bodyWidth = { widthOf(it, cellStyle) }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(even),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            plan.unitNote?.let { note ->
                Text(
                    text = note,
                    color = SolColors.Muted,
                    style = type.caption,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp)
                )
            }
            val gap = plan.gap.dp
            RateTableRow(
                label = "",
                off = plan.offHeader,
                mid = plan.midHeader,
                peak = plan.peakHeader,
                widths = plan,
                gap = gap,
                labelColor = SolColors.Muted,
                offColor = SolColors.Live.copy(alpha = 0.75f),
                midColor = SolColors.Forecast.copy(alpha = 0.85f),
                peakColor = SolColors.TouPeak.copy(alpha = 1f),
                textStyle = headerStyle,
                rowColor = stripe,
                verticalPadding = rowPad
            )
            plan.labels.forEachIndexed { index, label ->
                RateTableRow(
                    label = label,
                    off = plan.offValues[index],
                    mid = plan.midValues[index],
                    peak = plan.peakValues[index],
                    widths = plan,
                    gap = gap,
                    textStyle = cellStyle,
                    rowColor = if (index % 2 == 0) even else stripe,
                    verticalPadding = rowPad
                )
            }
        }
    }
}

@Composable
private fun RateTableRow(
    label: String,
    off: String,
    mid: String,
    peak: String,
    widths: RateTablePlan,
    gap: Dp,
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
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        RateCell(label, labelColor, textStyle, widths.labelWidth)
        RateCell(off, offColor, textStyle, widths.offWidth)
        RateCell(mid, midColor, textStyle, widths.midWidth)
        RateCell(peak, peakColor, textStyle, widths.peakWidth)
    }
}

@Composable
private fun RateCell(
    text: String,
    color: Color,
    textStyle: TextStyle,
    widthDp: Float
) {
    Text(
        text = text,
        color = color,
        style = textStyle,
        modifier = Modifier.width(widthDp.dp),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}


