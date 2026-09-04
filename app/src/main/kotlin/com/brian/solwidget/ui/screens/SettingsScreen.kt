package com.brian.solwidget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.IntentUtils
import com.brian.solwidget.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SolColors.Navy,
                    titleContentColor = SolColors.Ink
                )
            )
        },
        containerColor = SolColors.Navy
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "This app downloads the shared rooftop cache. " +
                    "The Cloudflare Worker is the only client that calls Solcast.",
                style = MaterialTheme.typography.bodyMedium,
                color = SolColors.Muted
            )
            OutlinedTextField(
                value = state.cacheUrl,
                onValueChange = viewModel::onCacheUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cache URL") },
                singleLine = true
            )
            OutlinedTextField(
                value = state.resourceId,
                onValueChange = viewModel::onResourceIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rooftop resource ID") },
                singleLine = true
            )
            OutlinedTextField(
                value = state.placeQuery,
                onValueChange = viewModel::onPlaceQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("City or ZIP for weather") },
                singleLine = true
            )
            if (state.placeLabel.isNotBlank()) {
                Text(
                    "Resolved: ${state.placeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SolColors.Live
                )
            }
            Text(
                "Weather uses Open-Meteo (no extra API key) and does not count against the Solcast daily limit.",
                style = MaterialTheme.typography.bodySmall,
                color = SolColors.Muted
            )
            state.errorMessage?.let { message ->
                Text(message, color = SolColors.Forecast, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Hobbyist Solcast accounts allow 10 requests per UTC day. " +
                    "The Worker pulls at most 5 times per day (about every 5 hours). " +
                    "Refresh in this app only re-downloads the shared cache.",
                style = MaterialTheme.typography.bodySmall,
                color = SolColors.Muted
            )
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saved) "Saved" else "Save and fetch")
            }
            TextButton(
                onClick = { IntentUtils.openUrl(context, "https://toolkit.solcast.com.au/") }
            ) {
                Text("Open Solcast Toolkit")
            }
        }
    }
}
