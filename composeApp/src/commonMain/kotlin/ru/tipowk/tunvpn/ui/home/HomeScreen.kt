package ru.tipowk.tunvpn.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.theme.ConnectedGreen
import ru.tipowk.tunvpn.theme.ConnectingYellow
import ru.tipowk.tunvpn.theme.DisconnectedGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToServers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TunVPN") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onNavigateToServers) {
                        Icon(Icons.Default.Storage, contentDescription = "Servers")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Current server name
            Text(
                text = uiState.activeServer?.name ?: "No server selected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Connect button
            ConnectButton(
                connectionState = uiState.connectionState,
                onClick = { viewModel.toggleConnection() },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status text
            Text(
                text = when (uiState.connectionState) {
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.DISCONNECTING -> "Disconnecting..."
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Proxy info card (shown when connected)
            AnimatedVisibility(
                visible = uiState.connectionState == ConnectionState.CONNECTED,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ProxyInfoCard(
                    onCopySocks = { viewModel.copyToClipboard("127.0.0.1:10808") },
                    onCopyHttp = { viewModel.copyToClipboard("127.0.0.1:10809") },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Traffic stats
            AnimatedVisibility(
                visible = uiState.connectionState == ConnectionState.CONNECTED,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TrafficStatsRow(
                    uploadSpeed = uiState.trafficStats.uploadSpeed,
                    downloadSpeed = uiState.trafficStats.downloadSpeed,
                    totalUpload = uiState.trafficStats.totalUpload,
                    totalDownload = uiState.trafficStats.totalDownload,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProxyInfoCard(
    onCopySocks: () -> Unit,
    onCopyHttp: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Proxy Addresses",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProxyAddressRow(
                label = "SOCKS5",
                address = "127.0.0.1:10808",
                onCopy = onCopySocks,
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProxyAddressRow(
                label = "HTTP",
                address = "127.0.0.1:10809",
                onCopy = onCopyHttp,
            )
        }
    }
}

@Composable
private fun ProxyAddressRow(
    label: String,
    address: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(70.dp),
        )

        Text(
            text = address,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    connectionState: ConnectionState,
    onClick: () -> Unit,
) {
    val buttonColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.DISCONNECTED -> DisconnectedGray
            ConnectionState.CONNECTING -> ConnectingYellow
            ConnectionState.CONNECTED -> ConnectedGreen
            ConnectionState.DISCONNECTING -> ConnectingYellow
        },
        animationSpec = tween(500),
    )

    // Pulsing animation for connecting state
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val scale = if (connectionState == ConnectionState.CONNECTING ||
        connectionState == ConnectionState.DISCONNECTING
    ) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                enabled = connectionState == ConnectionState.DISCONNECTED ||
                        connectionState == ConnectionState.CONNECTED,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Power,
            contentDescription = "Connect",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun TrafficStatsRow(
    uploadSpeed: Long,
    downloadSpeed: Long,
    totalUpload: Long,
    totalDownload: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "↑ ${formatSpeed(uploadSpeed)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatBytes(totalUpload),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "↓ ${formatSpeed(downloadSpeed)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatBytes(totalDownload),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
        bytesPerSecond < 1024 * 1024 -> "${"%.1f".format(bytesPerSecond / 1024.0)} KB/s"
        else -> "${"%.1f".format(bytesPerSecond / (1024.0 * 1024.0))} MB/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
