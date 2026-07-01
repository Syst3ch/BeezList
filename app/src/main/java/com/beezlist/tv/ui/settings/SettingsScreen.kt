package com.beezlist.tv.ui.settings

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import com.beezlist.tv.ui.common.TvButton
import androidx.tv.material3.Text
import com.beezlist.tv.EpgState
import com.beezlist.tv.PlaylistState
import com.beezlist.tv.R

private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun SettingsScreen(
    playlistUrls: List<String>,
    playlistState: PlaylistState,
    epgUrl: String?,
    epgState: EpgState,
    allGroupTitles: List<String>,
    hiddenGroupTitles: Set<String>,
    lockedGroupTitles: Set<String>,
    parentalPin: String?,
    profiles: List<String>,
    activeProfile: String,
    onAddPlaylist: (String) -> Unit,
    onRemovePlaylist: (String) -> Unit,
    onLoadEpg: (String) -> Unit,
    onToggleGroupHidden: (String, Boolean) -> Unit,
    onToggleGroupLocked: (String, Boolean) -> Unit,
    onSetParentalPin: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onExportSettings: ((String) -> Unit) -> Unit,
    onImportSettings: (String) -> Unit,
    onScanQr: () -> Unit,
    onScanEpgQr: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var newPlaylistUrl by remember { mutableStateOf("") }
    var epgUrlInput by remember(epgUrl) { mutableStateOf(epgUrl.orEmpty()) }
    var newProfileName by remember { mutableStateOf("") }
    var pinInput by remember(parentalPin) { mutableStateOf(parentalPin.orEmpty()) }
    var importInput by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
    )

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

            Text(
                text = stringResource(R.string.settings_playlists_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            playlistUrls.forEach { url ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M3Text(
                        text = url,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = 420.dp),
                    )
                    TvButton(onClick = { onRemovePlaylist(url) }) {
                        M3Text(stringResource(R.string.remove_playlist_button))
                    }
                }
            }
            OutlinedTextField(
                value = newPlaylistUrl,
                onValueChange = { newPlaylistUrl = it },
                label = { M3Text(stringResource(R.string.playlist_url_label)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )

            TvTvButton(onClick = {
                onAddPlaylist(newPlaylistUrl)
                newPlaylistUrl = ""
            }) {
                M3Text(stringResource(R.string.add_playlist_button))
            }

            TvTvButton(onClick = onScanQr) {
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

            Text(
                text = stringResource(R.string.settings_profiles_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            profiles.forEach { profile ->
                val isActive = profile == activeProfile
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(
                        onClick = { onSelectProfile(profile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        M3Text(profile)
                    }
                    TvButton(onClick = { onRemoveProfile(profile) }) {
                        M3Text(stringResource(R.string.remove_profile_button))
                    }
                }
            }
            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = { M3Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )
            TvButton(onClick = {
                onAddProfile(newProfileName)
                newProfileName = ""
            }) {
                M3Text(stringResource(R.string.add_profile_button))
            }

            OutlinedTextField(
                value = epgUrlInput,
                onValueChange = { epgUrlInput = it },
                label = { M3Text(stringResource(R.string.epg_url_label)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )

            TvTvButton(onClick = { onLoadEpg(epgUrlInput) }) {
                M3Text(stringResource(R.string.load_epg))
            }

            TvTvButton(onClick = onScanEpgQr) {
                M3Text(stringResource(R.string.scan_epg_qr_button))
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
                        val isLocked = group in lockedGroupTitles
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
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Checkbox(
                                checked = isLocked,
                                onCheckedChange = { checked -> onToggleGroupLocked(group, checked) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            M3Text(stringResource(R.string.lock_group_label), color = Color.White)
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.parental_pin_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it },
                label = { M3Text(stringResource(R.string.parental_pin_label)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )
            TvButton(onClick = { onSetParentalPin(pinInput) }) {
                M3Text(stringResource(R.string.save_pin_button))
            }

            Text(
                text = stringResource(R.string.backup_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TvButton(onClick = {
                onExportSettings { json ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            }) {
                M3Text(stringResource(R.string.export_settings_button))
            }
            OutlinedTextField(
                value = importInput,
                onValueChange = { importInput = it },
                label = { M3Text(stringResource(R.string.import_settings_label)) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 560.dp),
            )
            TvButton(onClick = {
                onImportSettings(importInput)
                importInput = ""
            }) {
                M3Text(stringResource(R.string.import_settings_button))
            }

            TvButton(onClick = onBack) {
                M3Text(stringResource(R.string.settings_back))
            }
        }
    }
}
