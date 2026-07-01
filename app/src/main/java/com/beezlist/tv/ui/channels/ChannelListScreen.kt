package com.beezlist.tv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.beezlist.tv.R
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.EpgProgram
import com.beezlist.tv.ui.common.TvButton

private const val ALL_TAB = "__all__"
private const val FAVORITES_TAB = "__favorites__"
private const val POPULAR_CHANNELS_LIMIT = 10

private data class PendingUnlock(val groupTitle: String, val selectTabAfter: Boolean)

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
    recentlyWatched: List<String>,
    lockedGroupTitles: Set<String>,
    parentalPin: String?,
    newChannelUrls: Set<String>,
    watchTimeTotals: Map<String, Long>,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onMoveChannel: (Channel, Channel) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEpg: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ALL_TAB) }
    var liveNowOnly by remember { mutableStateOf(false) }
    var unlockedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingUnlock by remember { mutableStateOf<PendingUnlock?>(null) }
    var pinError by remember { mutableStateOf(false) }

    val allChannelsLabel = stringResource(R.string.all_channels)
    val favoritesLabel = stringResource(R.string.favorites_group)
    val continueWatchingLabel = stringResource(R.string.continue_watching_group)
    val newChannelsLabel = stringResource(R.string.new_channels_group)
    val popularChannelsLabel = stringResource(R.string.popular_channels_group)

    fun isGroupLocked(rawGroupTitle: String): Boolean =
        rawGroupTitle in lockedGroupTitles && !parentalPin.isNullOrBlank() && rawGroupTitle !in unlockedGroups

    fun requestGroupAccess(rawGroupTitle: String, selectTabAfter: Boolean) {
        if (isGroupLocked(rawGroupTitle)) {
            pinError = false
            pendingUnlock = PendingUnlock(rawGroupTitle, selectTabAfter)
        } else if (selectTabAfter) {
            selectedTab = rawGroupTitle
        }
    }

    val allGroupTitlesRaw = remember(channels) {
        channels.map { it.groupTitle }.distinct().sorted()
    }
    val tabs = remember(allGroupTitlesRaw, allChannelsLabel, favoritesLabel) {
        listOf(ALL_TAB to allChannelsLabel, FAVORITES_TAB to favoritesLabel) +
            allGroupTitlesRaw.map { raw -> raw to raw.ifBlank { allChannelsLabel } }
    }

    val filteredChannels = remember(channels, query, liveNowOnly, epgPrograms) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val base = if (tokens.isEmpty()) {
            channels
        } else {
            channels.filter { channel ->
                val haystack = (channel.name + " " + channel.groupTitle).lowercase()
                tokens.all { haystack.contains(it) }
            }
        }
        if (liveNowOnly) {
            base.filter { currentProgramTitle(it.tvgId, epgPrograms) != null }
        } else {
            base
        }
    }
    val favoriteChannels = remember(filteredChannels, favorites) {
        filteredChannels.filter { it.streamUrl in favorites }
    }
    val groups = remember(filteredChannels) {
        filteredChannels.groupBy { it.groupTitle }.toSortedMap()
    }
    val recentChannels = remember(recentlyWatched, channels, liveNowOnly, epgPrograms, query) {
        if (query.isNotBlank()) return@remember emptyList()
        val list = recentlyWatched.mapNotNull { url -> channels.find { it.streamUrl == url } }
        if (liveNowOnly) list.filter { currentProgramTitle(it.tvgId, epgPrograms) != null } else list
    }
    val newChannels = remember(channels, newChannelUrls, liveNowOnly, epgPrograms, query) {
        if (query.isNotBlank()) return@remember emptyList()
        val list = channels.filter { it.streamUrl in newChannelUrls }
        if (liveNowOnly) list.filter { currentProgramTitle(it.tvgId, epgPrograms) != null } else list
    }
    val popularChannels = remember(channels, watchTimeTotals, liveNowOnly, epgPrograms, query) {
        if (query.isNotBlank()) return@remember emptyList()
        val list = channels.filter { (watchTimeTotals[it.streamUrl] ?: 0L) > 0L }
            .sortedByDescending { watchTimeTotals[it.streamUrl] ?: 0L }
            .take(POPULAR_CHANNELS_LIMIT)
        if (liveNowOnly) list.filter { currentProgramTitle(it.tvgId, epgPrograms) != null } else list
    }
    val heroChannel = remember(recentChannels, favoriteChannels, channels, query) {
        if (query.isNotBlank()) return@remember null
        recentChannels.firstOrNull() ?: favoriteChannels.firstOrNull() ?: channels.firstOrNull()
    }

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
            TvButton(
                onClick = { liveNowOnly = !liveNowOnly },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (liveNowOnly) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.surface,
                    contentColor = if (liveNowOnly) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                M3Text(stringResource(R.string.live_now_only_label))
            }
            TvButton(onClick = onOpenEpg) {
                M3Text(stringResource(R.string.epg_button))
            }
            TvButton(onClick = onOpenSettings) {
                M3Text(stringResource(R.string.settings_button))
            }
        }

        CategoryTabs(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { key ->
                if (key == ALL_TAB || key == FAVORITES_TAB) {
                    selectedTab = key
                } else {
                    requestGroupAccess(key, selectTabAfter = true)
                }
            },
        )

        if (selectedTab == ALL_TAB) {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (heroChannel != null) {
                    item {
                        HeroBanner(
                            channel = heroChannel,
                            programTitle = currentProgramTitle(heroChannel.tvgId, epgPrograms),
                            onPlay = { onChannelClick(heroChannel) },
                        )
                    }
                }

                if (recentChannels.isNotEmpty()) {
                    item { SectionTitle(continueWatchingLabel) }
                    item {
                        ChannelRow(
                            channels = recentChannels,
                            favorites = favorites,
                            epgPrograms = epgPrograms,
                            newChannelUrls = newChannelUrls,
                            onChannelClick = onChannelClick,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }

                if (favoriteChannels.isNotEmpty()) {
                    item { SectionTitle(favoritesLabel) }
                    item {
                        ChannelRow(
                            channels = favoriteChannels,
                            favorites = favorites,
                            epgPrograms = epgPrograms,
                            newChannelUrls = newChannelUrls,
                            onChannelClick = onChannelClick,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }

                if (newChannels.isNotEmpty()) {
                    item { SectionTitle(newChannelsLabel) }
                    item {
                        ChannelRow(
                            channels = newChannels,
                            favorites = favorites,
                            epgPrograms = epgPrograms,
                            newChannelUrls = newChannelUrls,
                            onChannelClick = onChannelClick,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }

                if (popularChannels.isNotEmpty()) {
                    item { SectionTitle(popularChannelsLabel) }
                    item {
                        ChannelRow(
                            channels = popularChannels,
                            favorites = favorites,
                            epgPrograms = epgPrograms,
                            newChannelUrls = newChannelUrls,
                            onChannelClick = onChannelClick,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }

                groups.forEach { (rawGroupTitle, groupChannels) ->
                    val displayLabel = rawGroupTitle.ifBlank { allChannelsLabel }
                    item { SectionTitle(displayLabel) }
                    if (isGroupLocked(rawGroupTitle)) {
                        item {
                            LockedGroupPlaceholder(
                                onClick = { requestGroupAccess(rawGroupTitle, selectTabAfter = false) },
                            )
                        }
                    } else {
                        item {
                            ChannelRow(
                                channels = groupChannels,
                                favorites = favorites,
                                epgPrograms = epgPrograms,
                                newChannelUrls = newChannelUrls,
                                onChannelClick = onChannelClick,
                                onToggleFavorite = onToggleFavorite,
                                onMoveChannel = onMoveChannel,
                            )
                        }
                    }
                }
            }
        } else {
            val isFavTab = selectedTab == FAVORITES_TAB
            val tabChannels = if (isFavTab) {
                favoriteChannels
            } else {
                filteredChannels.filter { it.groupTitle == selectedTab }
            }
            val tabLocked = !isFavTab && isGroupLocked(selectedTab)

            when {
                tabLocked -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LockedGroupPlaceholder(
                            onClick = { requestGroupAccess(selectedTab, selectTabAfter = false) },
                        )
                    }
                }
                tabChannels.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty_playlist),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    TvLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 24.dp),
                    ) {
                        item {
                            ChannelRow(
                                channels = tabChannels,
                                favorites = favorites,
                                epgPrograms = epgPrograms,
                                newChannelUrls = newChannelUrls,
                                onChannelClick = onChannelClick,
                                onToggleFavorite = onToggleFavorite,
                                onMoveChannel = onMoveChannel,
                            )
                        }
                    }
                }
            }
        }
    }

    val unlockTarget = pendingUnlock
    if (unlockTarget != null) {
        PinDialog(
            showError = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == parentalPin) {
                    unlockedGroups = unlockedGroups + unlockTarget.groupTitle
                    if (unlockTarget.selectTabAfter) {
                        selectedTab = unlockTarget.groupTitle
                    }
                    pendingUnlock = null
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onDismiss = { pendingUnlock = null },
        )
    }
}

@Composable
private fun PinDialog(
    showError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { M3Text(stringResource(R.string.parental_pin_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { M3Text(stringResource(R.string.parental_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (showError) {
                    M3Text(
                        text = stringResource(R.string.parental_pin_error),
                        color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                    )
                }
            }
        },
        confirmButton = {
            TvButton(onClick = { onConfirm(pin) }) {
                M3Text(stringResource(R.string.parental_pin_confirm))
            }
        },
        dismissButton = {
            TvButton(onClick = onDismiss) {
                M3Text(stringResource(R.string.parental_pin_cancel))
            }
        },
    )
}

@Composable
private fun LockedGroupPlaceholder(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(80.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.locked_group_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryTabs(
    tabs: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tabs) { (key, label) ->
            val isSelected = key == selected
            TvButton(
                onClick = { onSelect(key) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
            ) {
                M3Text(label)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChannelRow(
    channels: List<Channel>,
    favorites: Set<String>,
    epgPrograms: Map<String, List<EpgProgram>>,
    newChannelUrls: Set<String> = emptySet(),
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onMoveChannel: ((Channel, Channel) -> Unit)? = null,
) {
    val indexedChannels = channels.withIndex().toList()
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(indexedChannels) { (index, channel) ->
            ChannelTile(
                channel = channel,
                isFavorite = channel.streamUrl in favorites,
                isNew = channel.streamUrl in newChannelUrls,
                programTitle = currentProgramTitle(channel.tvgId, epgPrograms),
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel) },
                onMoveUp = if (onMoveChannel != null && index > 0) {
                    { onMoveChannel(channel, channels[index - 1]) }
                } else {
                    null
                },
                onMoveDown = if (onMoveChannel != null && index < channels.lastIndex) {
                    { onMoveChannel(channel, channels[index + 1]) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun HeroBanner(
    channel: Channel,
    programTitle: String?,
    onPlay: () -> Unit,
) {
    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(260.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ),
                    ),
                ),
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = 0.4f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(32.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(32.dp)
                    .fillMaxWidth(0.7f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!programTitle.isNullOrBlank()) {
                    Text(
                        text = programTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TvButton(onClick = onPlay) {
                    M3Text(stringResource(R.string.play_now))
                }
            }
        }
    }
}

@Composable
private fun ChannelTile(
    channel: Channel,
    isFavorite: Boolean,
    isNew: Boolean = false,
    programTitle: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val showReorder = onMoveUp != null || onMoveDown != null
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(if (showReorder) 220.dp else 184.dp),
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
                if (isNew) {
                    M3Text(
                        text = stringResource(R.string.new_channel_badge),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp),
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
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    M3Text(if (isFavorite) "★" else "☆")
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
            if (showReorder) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        modifier = Modifier.size(28.dp),
                    ) {
                        M3Text("▲")
                    }
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(28.dp),
                    ) {
                        M3Text("▼")
                    }
                }
            }
        }
    }
}
