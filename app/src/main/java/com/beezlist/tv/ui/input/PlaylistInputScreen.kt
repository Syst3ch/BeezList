package com.beezlist.tv.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beezlist.tv.PlaylistState
import com.beezlist.tv.R

private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun PlaylistInputScreen(
    lastUrl: String?,
    playlistState: PlaylistState,
    onLoadPlaylist: (String) -> Unit,
) {
    var url by remember(lastUrl) { mutableStateOf(lastUrl.orEmpty()) }

    LaunchedEffect(lastUrl) {
        if (url.isEmpty() && !lastUrl.isNullOrEmpty()) {
            url = lastUrl
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_input_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.playlist_input_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
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
                modifier = Modifier.width(560.dp),
            )

            Button(onClick = { onLoadPlaylist(url) }) {
                Text(stringResource(R.string.load_playlist))
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
        }
    }
}
