package com.example.template.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Simple example ViewModel demonstrating:
 * - StateFlow for reactive UI state
 * - Loading / error / data pattern
 * - Coroutine usage in viewModelScope
 */
data class HomeUiState(
    val message: String = "Hello from ViewModel",
    val clickCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun incrementCounter() {
        _uiState.update { it.copy(clickCount = it.clickCount + 1) }
    }

    fun simulateWork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Simulate some async work
            kotlinx.coroutines.delay(800)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = "Work completed! Count is now ${it.clickCount}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
