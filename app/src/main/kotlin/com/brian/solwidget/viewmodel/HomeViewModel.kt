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
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.HistoryApi
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.LivePastRepository
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.WeatherRepository
import com.brian.solwidget.data.WeatherSnapshot
import com.brian.solwidget.data.historyFetchKey
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class HomeTab { LIVE, HISTORY }

private data class HistoryBounds(
    val from: LocalDate,
    val toExclusive: LocalDate,
    val earliest: LocalDate?
)

fun homeTabStored(name: String?): HomeTab = when (name) {
    "history", "history-day" -> HomeTab.HISTORY
    else -> HomeTab.LIVE
}

fun HomeTab.storedName(): String = if (this == HomeTab.HISTORY) "history" else "live"

data class HomeUiState(
    val snapshot: ForecastSnapshot? = null,
    val weather: WeatherSnapshot? = null,
    val isLoading: Boolean = true,
    val now: Instant = Instant.now(),
    val layers: ChartLayers = ChartLayers(),
    val toastMessage: String? = null,
    val tab: HomeTab = HomeTab.LIVE,
    val historyMonth: YearMonth? = null,
    val selectedWeek: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    /** Opening History shows the current week until a day cell is tapped. */
    val historyWeekSelected: Boolean = true,
    val history: HistoryPage? = null,
    val historyKey: String? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val livePast: List<PowerPoint>? = null,
    val liveEcobee: List<EcobeeSample> = emptyList(),
    val livePastKey: String? = null,
    val gasDollarsPerHour: Double = GAS_AUX_DOLLARS_PER_HOUR,
    val chartExpanded: Boolean = false,
    /** Live fullscreen page. 0 is the power chart, then Solar, Temperature, and the other cards. */
    val fullscreenPage: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ForecastRepository.get(application)
    private val weatherRepo = WeatherRepository.get(application)
    private val storage = AppStorage(application)
    private val historyApi = HistoryApi()
    private val livePastRepository = LivePastRepository(application)
    private var historyGeneration = 0
    private var liveGeneration = 0

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val savedTab = runBlocking(Dispatchers.IO) { homeTabStored(storage.lastHomeTab()) }
        if (savedTab == HomeTab.HISTORY) {
            _uiState.update { it.copy(tab = savedTab) }
            loadHistory()
        }
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
                    gasDollarsPerHour = storage.gasAuxDollarsOnce()
                )
            }
            refresh(force = true)
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _uiState.update { it.copy(now = Instant.now()) }
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
            _uiState.update { it.copy(livePast = past.points, liveEcobee = past.ecobee, livePastKey = key) }
        }
        return result.isSuccess
    }

    fun setChartExpanded(expanded: Boolean) {
        if (_uiState.value.chartExpanded == expanded && (!expanded || _uiState.value.fullscreenPage == 0)) return
        _uiState.update {
            it.copy(
                chartExpanded = expanded,
                fullscreenPage = if (expanded) it.fullscreenPage else 0
            )
        }
    }

    fun setFullscreenPage(index: Int) {
        val page = index.coerceAtLeast(0)
        if (_uiState.value.fullscreenPage == page) return
        _uiState.update { it.copy(fullscreenPage = page) }
    }

    fun showTab(tab: HomeTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        viewModelScope.launch { storage.setLastHomeTab(tab.storedName()) }
        if (tab == HomeTab.HISTORY) loadHistory()
    }

    fun shiftHistoryMonth(delta: Int) {
        val today = DetroitCalendar.today(_uiState.value.now)
        val current = _uiState.value.historyMonth ?: YearMonth.from(today)
        if (!DetroitCalendar.canShiftMonth(current, delta, today)) return
        _uiState.update { state ->
            val next = current.plusMonths(delta.toLong())
            val weeks = DetroitCalendar.monthWeeks(next)
            val selected = weeks.find { it.id == state.selectedWeek }?.id
                ?: weeks.find { week -> week.days.any { it.date == today } }?.id
                ?: weeks.firstOrNull()?.id
            val dayStillVisible = state.selectedDay != null &&
                weeks.any { week -> week.days.any { it.date == state.selectedDay } }
            val nextDay = when {
                dayStillVisible -> state.selectedDay
                weeks.any { week -> week.days.any { it.date == today && it.inMonth } } -> today
                else -> LocalDate.of(next.year, next.monthValue, 1)
            }
            state.copy(historyMonth = next, selectedWeek = selected, selectedDay = nextDay)
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
        val start = DetroitCalendar.weekStart(sunday)
        if (start.isAfter(DetroitCalendar.weekStart(today))) return
        _uiState.update { it.copy(selectedWeek = start, historyWeekSelected = true) }
    }

    fun selectHistoryDay(date: LocalDate) {
        val today = DetroitCalendar.today(_uiState.value.now)
        if (date.isAfter(today)) return
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
        selectHistoryDay(day.plusDays(delta.toLong()))
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
        _uiState.update { it.copy(historyMonth = focus.month, selectedWeek = focus.week) }
        if (monthChanged) loadHistory()
    }

    /**
     * Loads the visible month once. Week taps reuse that page.
     * A failed fetch keeps the previous page and shows [HomeUiState.historyError].
     */
    fun loadHistory() {
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
        if (current.historyKey == key && current.history != null && current.historyError == null) return
        val generation = ++historyGeneration
        _uiState.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { historyApi.fetch(storage.cacheUrlOnce(), from, to) }
            }
            if (generation != historyGeneration) return@launch
            _uiState.update { state ->
                result.fold(
                    onSuccess = { page ->
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

    fun resetLayers() {
        viewModelScope.launch {
            ChartLayer.entries.forEach { storage.setChartLayer(it, true) }
            _uiState.update { it.copy(layers = ChartLayers()) }
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
