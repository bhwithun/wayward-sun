package com.brian.solwidget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.WeatherRepository
import com.brian.solwidget.data.WeatherSnapshot
import com.brian.solwidget.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshot: ForecastSnapshot? = null,
    val weather: WeatherSnapshot? = null,
    val isLoading: Boolean = true,
    val hasApiKey: Boolean = false,
    val now: Instant = Instant.now(),
    val layers: ChartLayers = ChartLayers(),
    val toastMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ForecastRepository.get(application)
    private val weatherRepo = WeatherRepository.get(application)
    private val storage = AppStorage(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = repo.loadSnapshot()
            val weatherCached = weatherRepo.loadSnapshot()
            val hasKey = storage.apiKeyOnce().isNotBlank()
            _uiState.update {
                it.copy(
                    snapshot = cached,
                    weather = weatherCached,
                    isLoading = false,
                    hasApiKey = hasKey,
                    now = Instant.now(),
                    layers = storage.chartLayersOnce()
                )
            }
            refresh(force = cached.fetchedAt == null && hasKey)
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _uiState.update { it.copy(now = Instant.now()) }
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
                    hasApiKey = !snapshot.isDemo || snapshot.errorMessage?.contains("API key") != true,
                    now = Instant.now(),
                    toastMessage = snapshot.errorMessage?.takeIf { it.isSolcastQuotaMessage() }
                )
            }
            WidgetUpdater.updateAll(getApplication())
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
