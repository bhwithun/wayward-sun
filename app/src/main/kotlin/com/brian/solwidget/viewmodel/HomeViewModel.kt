package com.brian.solwidget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.GAS_AUX_DOLLARS_PER_HOUR
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.data.HouseDay
import com.brian.solwidget.data.HouseSample
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.HistoryApi
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.LivePastRepository
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.WeatherRepository
import com.brian.solwidget.data.WeatherSnapshot
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.data.withDay
import com.brian.solwidget.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class HistoryBounds(
    val from: LocalDate,
    val toExclusive: LocalDate,
    val earliest: LocalDate?
)

/** Local clock for the now-line. It never fetches anything by itself. */
const val HOME_CLOCK_MS = 30_000L

/**
 * While today's day chart is open and the app is in the foreground, today's
 * rows (one Detroit day, about 60 KB) are fetched again this often. The month
 * page is never re-fetched on a timer; 2.10.0 did that every 30 seconds.
 */
const val TODAY_POLL_MS = 5 * 60 * 1000L

/** The app opens here. A null [selectedDay] means today, until the first load fills it in. */
fun viewingTodayDay(weekSelected: Boolean, selectedDay: LocalDate?, today: LocalDate): Boolean =
    !weekSelected && (selectedDay == null || selectedDay == today)

/** The date-row control is hidden while today's day chart is already showing. */
fun showTodayButton(weekSelected: Boolean, selectedDay: LocalDate?, today: LocalDate): Boolean =
    !viewingTodayDay(weekSelected, selectedDay, today)

/**
 * Day to show after the month changes by the arrows, a calendar swipe, or paging.
 * Other months still open on the 1st (or on the day that is still in that grid).
 * Coming back to the current month selects today, including when the 1st of the
 * month you left is still drawn on this month's grid.
 */
fun dayForMonthShift(
    nextMonth: YearMonth,
    previousDay: LocalDate?,
    today: LocalDate,
    weeks: List<com.brian.solwidget.data.CalendarWeek>
): LocalDate {
    if (nextMonth == YearMonth.from(today)) return today
    val stillThere = previousDay != null &&
        weeks.any { week -> week.days.any { it.date == previousDay && it.inMonth } }
    if (stillThere) return previousDay
    return LocalDate.of(nextMonth.year, nextMonth.monthValue, 1)
}

/**
 * One day step from the chart pager. Stepping onto the 1st of the current month
 * from another month selects today. The 1st of any other month stays the 1st,
 * and a step that stays inside the month is unchanged.
 */
fun dayAfterPagingIntoMonth(target: LocalDate, from: LocalDate, today: LocalDate): LocalDate {
    val enteredCurrent = YearMonth.from(target) != YearMonth.from(from) &&
        YearMonth.from(target) == YearMonth.from(today) &&
        target.dayOfMonth == 1
    return if (enteredCurrent) today else target
}

/**
 * True when today's rows should be fetched again: the app is resumed, today's
 * day is the open chart, and [TODAY_POLL_MS] has passed since the last fetch.
 */
fun todayPollDue(sinceLastMs: Long, viewingToday: Boolean, foreground: Boolean): Boolean =
    foreground && viewingToday && sinceLastMs >= TODAY_POLL_MS

/**
 * When the open chart is the current day and the Detroit date rolls, follow it.
 * A week chart and any other selected day stay where they are. Returns the new
 * day, or null when nothing should change.
 */
fun followToday(
    weekSelected: Boolean,
    selectedDay: LocalDate?,
    previousToday: LocalDate,
    today: LocalDate
): LocalDate? {
    if (today == previousToday || weekSelected) return null
    if (selectedDay == null || selectedDay == previousToday) return today
    return null
}

data class HomeUiState(
    val snapshot: ForecastSnapshot? = null,
    val weather: WeatherSnapshot? = null,
    val isLoading: Boolean = true,
    val now: Instant = Instant.now(),
    val layers: ChartLayers = ChartLayers(),
    val toastMessage: String? = null,
    val historyMonth: YearMonth? = null,
    val selectedWeek: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    /** False on open, so the chart is today's day. A week chip turns this on. */
    val historyWeekSelected: Boolean = false,
    val history: HistoryPage? = null,
    val historyKey: String? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val livePast: List<PowerPoint>? = null,
    val liveEcobee: List<EcobeeSample> = emptyList(),
    val liveHouse: List<HouseSample> = emptyList(),
    val liveHouseDays: List<HouseDay> = emptyList(),
    val livePastKey: String? = null,
    val widgetHouse: Boolean = false,
    val gasDollarsPerHour: Double = GAS_AUX_DOLLARS_PER_HOUR,
    val chartExpanded: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ForecastRepository.get(application)
    private val weatherRepo = WeatherRepository.get(application)
    private val storage = AppStorage(application)
    private val historyApi = HistoryApi()
    private val livePastRepository = LivePastRepository(application)
    private var historyGeneration = 0
    private var liveGeneration = 0
    private var foreground = false
    private var lastTodayFetchMs = 0L
    private var todayFetching = false

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
        viewModelScope.launch {
            val cached = repo.loadSnapshot()
            val weatherCached = weatherRepo.loadSnapshot()
            _uiState.update {
                it.copy(
                    snapshot = cached,
                    weather = weatherCached,
                    isLoading = false,
                    now = Instant.now(),
                    layers = storage.chartLayersOnce(),
                    widgetHouse = storage.widgetHouseOnce(),
                    gasDollarsPerHour = storage.gasAuxDollarsOnce()
                )
            }
            refresh(force = true)
        }
        viewModelScope.launch {
            var previousToday = DetroitCalendar.today(Instant.now())
            while (true) {
                delay(HOME_CLOCK_MS)
                val now = Instant.now()
                val today = DetroitCalendar.today(now)
                val before = _uiState.value
                val followed = followToday(
                    before.historyWeekSelected,
                    before.selectedDay,
                    previousToday,
                    today
                )
                previousToday = today
                _uiState.update { state ->
                    if (followed == null) {
                        state.copy(now = now)
                    } else {
                        state.copy(
                            now = now,
                            selectedDay = followed,
                            historyMonth = YearMonth.from(followed),
                            selectedWeek = DetroitCalendar.weekStart(followed)
                        )
                    }
                }
                if (!foreground) continue
                pollTodayIfDue()
                if (loadLivePast(force = false)) {
                    WidgetUpdater.updateAll(getApplication())
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60 * 60 * 1000L)
                val weather = weatherRepo.refresh(force = false)
                _uiState.update { it.copy(weather = weather, now = Instant.now()) }
                WidgetUpdater.updateAll(getApplication())
            }
        }
    }

    /** Called from the screen on ON_RESUME and ON_PAUSE. Network polling runs only while resumed. */
    fun setForeground(resumed: Boolean) {
        foreground = resumed
        if (!resumed) return
        _uiState.update { it.copy(now = Instant.now()) }
        pollTodayIfDue()
    }

    /** Fetches today's Detroit day only and merges it into the open month page. */
    private fun pollTodayIfDue() {
        val state = _uiState.value
        val nowMs = System.currentTimeMillis()
        val today = DetroitCalendar.today(Instant.ofEpochMilli(nowMs))
        val viewing = viewingTodayDay(state.historyWeekSelected, state.selectedDay, today)
        if (!todayPollDue(nowMs - lastTodayFetchMs, viewing, foreground)) return
        val page = state.history ?: return
        if (state.historyLoading || todayFetching) return
        val dayStart = today.atStartOfDay(DetroitCalendar.ZONE).toInstant()
        if (dayStart.isBefore(page.from) || !dayStart.isBefore(page.to)) return
        val key = state.historyKey
        val generation = historyGeneration
        todayFetching = true
        lastTodayFetchMs = nowMs
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { historyApi.fetchDay(storage.cacheUrlOnce(), today) }
            }
            todayFetching = false
            if (generation != historyGeneration) return@launch
            result.onSuccess { day ->
                _uiState.update { current ->
                    val open = current.history
                    if (open == null || current.historyKey != key) current
                    else current.copy(history = open.withDay(day))
                }
            }
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val power = async { repo.refresh(force = force) }
            val weather = async { weatherRepo.refresh(force = force) }
            val snapshot = power.await()
            _uiState.update {
                it.copy(
                    snapshot = snapshot,
                    weather = weather.await(),
                    isLoading = false,
                    now = Instant.now(),
                    toastMessage = snapshot.errorMessage?.takeIf { it.isSolcastQuotaMessage() }
                )
            }
            loadLivePast(force = true)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    /**
     * Stored history supplies the green trace. The cache forecast array starts
     * at the last pull, so it has no estimated actuals for earlier days.
     * Returns true when a new series was stored, so the widget can redraw it.
     */
    private suspend fun loadLivePast(force: Boolean): Boolean {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val (from, to) = ForecastSnapshot.range(now, zone)
        val key = historyFetchKey(from, to)
        val current = _uiState.value
        if (!force && current.livePastKey == key && current.livePast != null) return false
        val generation = ++liveGeneration
        val result = livePastRepository.fetch(now, zone)
        if (generation != liveGeneration) return false
        result.onSuccess { past ->
            _uiState.update {
                it.copy(
                    livePast = past.points,
                    liveEcobee = past.ecobee,
                    liveHouse = past.house,
                    liveHouseDays = past.houseDays,
                    livePastKey = key
                )
            }
        }
        return result.isSuccess
    }

    fun setChartExpanded(expanded: Boolean) {
        if (_uiState.value.chartExpanded == expanded) return
        _uiState.update { it.copy(chartExpanded = expanded) }
    }

    fun shiftHistoryMonth(delta: Int) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val current = _uiState.value.historyMonth ?: YearMonth.from(today)
        if (!DetroitCalendar.canShiftMonth(current, delta, today)) return
        _uiState.update { state ->
            val next = current.plusMonths(delta.toLong())
            val weeks = DetroitCalendar.monthWeeks(next)
            val nextDay = dayForMonthShift(next, state.selectedDay, today, weeks)
            val selected = if (next == YearMonth.from(today)) {
                DetroitCalendar.weekStart(today)
            } else {
                weeks.find { it.id == state.selectedWeek }?.id
                    ?: weeks.find { week -> week.days.any { it.date == nextDay } }?.id
                    ?: weeks.firstOrNull()?.id
            }
            state.copy(historyMonth = next, selectedWeek = selected, selectedDay = nextDay)
        }
        loadHistory()
    }

    /** Date-row Today. Opens today's day and moves the calendar to this month. */
    fun showToday() {
        val today = DetroitCalendar.today(_uiState.value.now)
        _uiState.update {
            it.copy(
                historyMonth = YearMonth.from(today),
                selectedWeek = DetroitCalendar.weekStart(today),
                selectedDay = today,
                historyWeekSelected = false
            )
        }
        loadHistory()
    }

    /** Month title. Returns to today's week. */
    fun showCurrentHistory() {
        val today = DetroitCalendar.today(_uiState.value.now)
        _uiState.update {
            it.copy(
                historyMonth = YearMonth.from(today),
                selectedWeek = DetroitCalendar.weekStart(today),
                selectedDay = today,
                historyWeekSelected = true
            )
        }
        loadHistory()
    }

    fun selectHistoryWeek(sunday: LocalDate) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val forwardLimit = DetroitCalendar.forwardLimit(today)
        val start = DetroitCalendar.weekStart(sunday)
        if (start.isAfter(DetroitCalendar.weekStart(forwardLimit))) return
        _uiState.update { it.copy(selectedWeek = start, historyWeekSelected = true) }
    }

    fun selectHistoryDay(date: LocalDate) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val forwardLimit = DetroitCalendar.forwardLimit(today)
        if (date.isAfter(forwardLimit)) return
        val month = YearMonth.from(date)
        val monthChanged = _uiState.value.historyMonth != month
        _uiState.update { it.copy(selectedDay = date, historyMonth = month, historyWeekSelected = false) }
        if (monthChanged) loadHistory()
    }

    /** Swipe on the day chart. One step is the adjacent local day. */
    fun shiftHistoryDay(delta: Int) {
        val state = _uiState.value
        val today = DetroitCalendar.today(state.now)
        val day = state.selectedDay ?: today
        val window = state.history?.let { historyWindow(it) }
        if (!DetroitCalendar.canStepDay(day, delta, today, window?.from, window?.toExclusive, window?.earliest)) {
            return
        }
        selectHistoryDay(dayAfterPagingIntoMonth(day.plusDays(delta.toLong()), day, today))
    }

    /** Swipe on the history chart. One step is the adjacent Sunday–Saturday week. */
    fun shiftHistoryWeek(delta: Int) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val state = _uiState.value
        val month = state.historyMonth ?: YearMonth.from(today)
        val week = state.selectedWeek ?: DetroitCalendar.weekStart(today)
        val window = state.history?.let { historyWindow(it) }
        if (!DetroitCalendar.canStepWeek(week, delta, today, window?.from, window?.toExclusive, window?.earliest)) {
            return
        }
        val focus = DetroitCalendar.shiftWeek(month, week, delta)
        val monthChanged = focus.month != month || state.historyMonth == null
        val enteredCurrent = monthChanged && focus.month == YearMonth.from(today)
        _uiState.update {
            it.copy(
                historyMonth = focus.month,
                selectedWeek = focus.week,
                selectedDay = if (enteredCurrent) today else it.selectedDay
            )
        }
        if (monthChanged) loadHistory()
    }

    /**
     * Loads the visible month once. Week taps reuse that page.
     * A failed fetch keeps the previous page and shows [HomeUiState.historyError].
     */
    fun loadHistory(force: Boolean = false) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val month = _uiState.value.historyMonth ?: YearMonth.from(today)
        val weeks = DetroitCalendar.monthWeeks(month)
        if (weeks.isEmpty()) return
        val selected = _uiState.value.selectedWeek?.takeIf { sunday -> weeks.any { it.id == sunday } }
            ?: weeks.find { week -> week.days.any { it.date == today } }?.id
            ?: weeks.first().id
        val day = _uiState.value.selectedDay ?: today
        if (_uiState.value.historyMonth != month || _uiState.value.selectedWeek != selected || _uiState.value.selectedDay != day) {
            _uiState.update { it.copy(historyMonth = month, selectedWeek = selected, selectedDay = day) }
        }
        val (from, to) = DetroitCalendar.monthFetchRange(weeks)
        val key = historyFetchKey(from, to)
        val current = _uiState.value
        val havePage = current.historyKey == key && current.history != null && current.historyError == null
        if (!force && havePage) return
        val generation = ++historyGeneration
        _uiState.update {
            it.copy(
                historyLoading = !havePage,
                historyError = if (havePage) it.historyError else null
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { historyApi.fetch(storage.cacheUrlOnce(), from, to) }
            }
            if (generation != historyGeneration) return@launch
            _uiState.update { state ->
                result.fold(
                    onSuccess = { page ->
                        lastTodayFetchMs = System.currentTimeMillis()
                        state.copy(
                            history = page,
                            historyKey = key,
                            historyLoading = false,
                            historyError = null
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            historyLoading = false,
                            historyError = error.message ?: "Unable to load history."
                        )
                    }
                )
            }
        }
    }

    fun reloadGasRate() {
        viewModelScope.launch {
            _uiState.update { it.copy(gasDollarsPerHour = storage.gasAuxDollarsOnce()) }
        }
    }

    fun setLayer(layer: ChartLayer, visible: Boolean) {
        viewModelScope.launch {
            storage.setChartLayer(layer, visible)
            _uiState.update { it.copy(layers = it.layers.with(layer, visible)) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun setWidgetHouse(visible: Boolean) {
        viewModelScope.launch {
            storage.setWidgetHouse(visible)
            _uiState.update { it.copy(widgetHouse = visible) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun resetLayers() {
        viewModelScope.launch {
            val defaults = ChartLayers()
            for (layer in ChartLayer.entries) {
                storage.setChartLayer(layer, defaults.isEnabled(layer))
            }
            _uiState.update { it.copy(layers = defaults) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    private fun historyWindow(page: HistoryPage): HistoryBounds {
        val zone = DetroitCalendar.ZONE
        val samples = page.points.map { it.periodEnd.atZone(zone).toLocalDate() } +
            page.ecobee.map { it.at.atZone(zone).toLocalDate() }
        return HistoryBounds(
            from = page.from.atZone(zone).toLocalDate(),
            toExclusive = page.to.atZone(zone).toLocalDate(),
            earliest = samples.minOrNull()
        )
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}

fun String.isSolcastQuotaMessage(): Boolean =
    contains("request limit reached", ignoreCase = true) ||
        contains("quota reached", ignoreCase = true)
