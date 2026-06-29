package com.beezlist.tv.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.beezlist.tv.R
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.EpgProgram

private fun currentProgramTitle(tvgId: String?, epgPrograms: Map<String, List<EpgProgram>>): String? {
    if (tvgId.isNullOrBlank()) return null
    val now = System.currentTimeMillis()
    return epgPrograms[tvgId]?.firstOrNull { now in it.startMillis until it.stopMillis }?.title
}

@Composable
fun ChannelListScreen(
    channels: List<Channel>,
    favorites: Set<String>,
    epgPrograms: Map<String, List<EpgProgram>>,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEpg: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val allChannelsLabel = stringResource(R.string.all_channels)
    val favoritesLabel = stringResource(R.string.favorites_group)
    val filteredChannels = if (query.isBlank()) {
        channels
    } else {
        channels.filter { it.name.contains(query, ignoreCase = true) }
    }
    val favoriteChannels = filteredChannels.filter { it.streamUrl in favorites }
    val groups = filteredChannels
        .groupBy { it.groupTitle.ifBlank { allChannelsLabel } }
        .toSortedMap()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { M3Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onOpenEpg) {
                Text(stringResource(R.string.epg_button))
            }
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings_button))
            }
        }

        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (favoriteChannels.isNotEmpty()) {
                item {
                    Text(
                        text = favoritesLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
                item {
                    TvLazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(favoriteChannels) { channel ->
                            ChannelTile(
                                channel = channel,
                                isFavorite = true,
                                programTitle = currentProgramTitle(channel.tvgId, epgPrograms),
                                onClick = { onChannelClick(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                            )
                        }
                    }
                }
            }

            groups.forEach { (groupTitle, groupChannels) ->
                item {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
                item {
                    TvLazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(groupChannels) { channel ->
                            ChannelTile(
                                channel = channel,
                                isFavorite = channel.streamUrl in favorites,
                                programTitle = currentProgramTitle(channel.tvgId, epgPrograms),
                                onClick = { onChannelClick(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelTile(
    channel: Channel,
    isFavorite: Boolean,
    programTitle: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(184.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                )
                Button(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Text(if (isFavorite) "★" else "☆")
                }
            }
            if (!programTitle.isNullOrBlank()) {
                Text(
                    text = programTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
