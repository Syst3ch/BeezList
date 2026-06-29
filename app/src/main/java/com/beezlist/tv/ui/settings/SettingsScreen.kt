package com.beezlist.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beezlist.tv.EpgState
import com.beezlist.tv.PlaylistState
import com.beezlist.tv.R

private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun SettingsScreen(
    currentUrl: String?,
    playlistState: PlaylistState,
    epgUrl: String?,
    epgState: EpgState,
    allGroupTitles: List<String>,
    hiddenGroupTitles: Set<String>,
    onLoadPlaylist: (String) -> Unit,
    onLoadEpg: (String) -> Unit,
    onToggleGroupHidden: (String, Boolean) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var epgUrlInput by remember(epgUrl) { mutableStateOf(epgUrl.orEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { M3Text(stringResource(R.string.playlist_url_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )

            Button(onClick = { onLoadPlaylist(url) }) {
                M3Text(stringResource(R.string.load_playlist))
            }

            Button(onClick = onScanQr) {
                M3Text(stringResource(R.string.scan_qr_button))
            }

            when (playlistState) {
                is PlaylistState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.loading_playlist))
                    }
                }
                is PlaylistState.Error -> {
                    Text(
                        text = stringResource(R.string.error_loading_playlist),
                        color = ErrorRed,
                    )
                }
                else -> Unit
            }

            OutlinedTextField(
                value = epgUrlInput,
                onValueChange = { epgUrlInput = it },
                label = { M3Text(stringResource(R.string.epg_url_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )

            Button(onClick = { onLoadEpg(epgUrlInput) }) {
                M3Text(stringResource(R.string.load_epg))
            }

            when (epgState) {
                is EpgState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.loading_epg))
                    }
                }
                is EpgState.Error -> {
                    Text(
                        text = stringResource(R.string.error_loading_epg),
                        color = ErrorRed,
                    )
                }
                else -> Unit
            }

            if (allGroupTitles.isNotEmpty()) {
                val allChannelsLabel = stringResource(R.string.all_channels)
                Text(
                    text = stringResource(R.string.settings_groups_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    allGroupTitles.forEach { group ->
                        val isVisible = group !in hiddenGroupTitles
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isVisible,
                                onCheckedChange = { checked -> onToggleGroupHidden(group, !checked) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            M3Text(
                                text = group.ifBlank { allChannelsLabel },
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Button(onClick = onBack) {
                M3Text(stringResource(R.string.settings_back))
            }
        }
    }
}
