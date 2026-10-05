package com.brian.solwidget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
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
import kotlinx.coroutines.withContext

enum class HomeTab { LIVE, HISTORY }

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
    val history: HistoryPage? = null,
    val historyKey: String? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val livePast: List<PowerPoint>? = null,
    val livePastKey: String? = null
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
        viewModelScope.launch {
            val cached = repo.loadSnapshot()
            val weatherCached = weatherRepo.loadSnapshot()
            _uiState.update {
                it.copy(
                    snapshot = cached,
                    weather = weatherCached,
                    isLoading = false,
                    now = Instant.now(),
                    layers = storage.chartLayersOnce()
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
            _uiState.update { it.copy(livePast = past, livePastKey = key) }
        }
        return result.isSuccess
    }

    fun showTab(tab: HomeTab) {
        _uiState.update { it.copy(tab = tab) }
        if (tab == HomeTab.HISTORY) loadHistory()
    }

    fun shiftHistoryMonth(delta: Int) {
        val today = DetroitCalendar.today(_uiState.value.now)
        _uiState.update { state ->
            val current = state.historyMonth ?: YearMonth.from(today)
            val next = current.plusMonths(delta.toLong())
            val weeks = DetroitCalendar.monthWeeks(next)
            val selected = weeks.find { it.id == state.selectedWeek }?.id
                ?: weeks.find { week -> week.days.any { it.date == today } }?.id
                ?: weeks.firstOrNull()?.id
            state.copy(historyMonth = next, selectedWeek = selected)
        }
        loadHistory()
    }

    fun selectHistoryWeek(sunday: LocalDate) {
        _uiState.update { it.copy(selectedWeek = sunday) }
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
        if (_uiState.value.historyMonth != month || _uiState.value.selectedWeek != selected) {
            _uiState.update { it.copy(historyMonth = month, selectedWeek = selected) }
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

    fun setLayer(layer: ChartLayer, visible: Boolean) {
        viewModelScope.launch {
            storage.setChartLayer(layer, visible)
            _uiState.update { it.copy(layers = it.layers.with(layer, visible)) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}

fun String.isSolcastQuotaMessage(): Boolean =
    contains("request limit reached", ignoreCase = true) ||
        contains("quota reached", ignoreCase = true)
