package com.brian.solwidget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ForecastRepository
import com.brian.solwidget.data.WeatherRepository
import com.brian.solwidget.widget.WidgetUpdater
import com.brian.solwidget.work.RefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val resourceId: String = AppStorage.DEFAULT_RESOURCE_ID,
    val placeQuery: String = "",
    val placeLabel: String = "",
    val saved: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ForecastRepository.get(application)
    private val weatherRepo = WeatherRepository.get(application)
    private val storage = AppStorage(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    apiKey = storage.apiKeyOnce(),
                    resourceId = storage.resourceIdOnce(),
                    placeQuery = storage.placeQueryOnce(),
                    placeLabel = storage.placeLabelOnce()
                )
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, saved = false, errorMessage = null) }
    }

    fun onResourceIdChange(value: String) {
        _uiState.update { it.copy(resourceId = value, saved = false, errorMessage = null) }
    }

    fun onPlaceQueryChange(value: String) {
        _uiState.update { it.copy(placeQuery = value, saved = false, errorMessage = null) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repo.saveSettings(_uiState.value.apiKey, _uiState.value.resourceId)
                val query = _uiState.value.placeQuery.trim()
                if (query.isBlank()) {
                    storage.clearPlace()
                    _uiState.update { it.copy(placeLabel = "") }
                } else {
                    val place = weatherRepo.resolveAndSavePlace(query)
                    _uiState.update { it.copy(placeLabel = place.label) }
                    weatherRepo.refresh(force = true)
                }
                RefreshScheduler.ensure(getApplication())
                if (_uiState.value.apiKey.isNotBlank()) {
                    repo.refresh(force = true)
                    WidgetUpdater.updateAll(getApplication())
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = false,
                        errorMessage = error.message ?: "Could not save settings."
                    )
                }
            }
        }
    }
}
