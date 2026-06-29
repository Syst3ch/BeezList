package com.beezlist.tv.ui.pairing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beezlist.tv.R
import com.beezlist.tv.network.PairingServer
import com.beezlist.tv.network.generateQrBitmap
import com.beezlist.tv.network.localIpAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun QrPairingScreen(
    onUrlReceived: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var receivedUrl by remember { mutableStateOf<String?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val server = remember { PairingServer(onUrlReceived = { receivedUrl = it }) }
    val noNetworkError = stringResource(R.string.qr_pairing_error_no_network)
    val serverError = stringResource(R.string.qr_pairing_error_server)

    DisposableEffect(server) {
        onDispose { server.stop() }
    }

    LaunchedEffect(server) {
        val ip = withContext(Dispatchers.IO) { localIpAddress() }
        if (ip == null) {
            errorMessage = noNetworkError
            return@LaunchedEffect
        }
        try {
            withContext(Dispatchers.IO) { server.start() }
        } catch (e: Exception) {
            errorMessage = serverError
            return@LaunchedEffect
        }
        val url = "http://$ip:${server.listeningPort}/"
        pairingUrl = url
        qrBitmap = withContext(Dispatchers.Default) { generateQrBitmap(url, 480) }
    }

    LaunchedEffect(receivedUrl) {
        receivedUrl?.let(onUrlReceived)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.qr_pairing_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.qr_pairing_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            when {
                errorMessage != null -> Text(text = errorMessage.orEmpty(), color = ErrorRed)
                qrBitmap != null -> {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(280.dp),
                    )
                    Text(text = pairingUrl.orEmpty(), color = MaterialTheme.colorScheme.onBackground)
                    Text(text = stringResource(R.string.qr_pairing_waiting), color = MaterialTheme.colorScheme.onBackground)
                }
                else -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            Button(onClick = onCancel) {
                Text(stringResource(R.string.qr_pairing_cancel))
            }
        }
    }
}
