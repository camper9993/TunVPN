package ru.tipowk.tunvpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import ru.tipowk.tunvpn.model.Flow
import ru.tipowk.tunvpn.model.SecurityType
import ru.tipowk.tunvpn.model.TransportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: String?,
    onBack: () -> Unit,
    viewModel: ServerEditViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(serverId) {
        viewModel.loadServer(serverId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    val config = uiState.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditMode) "Edit Server" else "Add Server")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.save() }) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Basic fields
            OutlinedTextField(
                value = config.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.address,
                onValueChange = { viewModel.updateAddress(it) },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.port.toString(),
                onValueChange = { viewModel.updatePort(it) },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.uuid,
                onValueChange = { viewModel.updateUuid(it) },
                label = { Text("UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Transport
            SectionHeader("Transport")
            DropdownSelector(
                label = "Network",
                selected = config.network.label,
                options = TransportType.entries.map { it.label },
                onSelect = { label ->
                    TransportType.entries.find { it.label == label }
                        ?.let { viewModel.updateNetwork(it) }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Security
            DropdownSelector(
                label = "Security",
                selected = config.security.label,
                options = SecurityType.entries.map { it.label },
                onSelect = { label ->
                    SecurityType.entries.find { it.label == label }
                        ?.let { viewModel.updateSecurity(it) }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Flow
            DropdownSelector(
                label = "Flow",
                selected = config.flow.label,
                options = Flow.entries.map { it.label },
                onSelect = { label ->
                    Flow.entries.find { it.label == label }
                        ?.let { viewModel.updateFlow(it) }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            // TLS settings
            if (config.security == SecurityType.TLS || config.security == SecurityType.REALITY) {
                SectionHeader("TLS Settings")
                OutlinedTextField(
                    value = config.sni,
                    onValueChange = { viewModel.updateSni(it) },
                    label = { Text("SNI") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.fingerprint,
                    onValueChange = { viewModel.updateFingerprint(it) },
                    label = { Text("Fingerprint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (config.security == SecurityType.TLS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = config.allowInsecure,
                            onCheckedChange = { viewModel.updateAllowInsecure(it) },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow Insecure")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Reality settings
            if (config.security == SecurityType.REALITY) {
                SectionHeader("Reality Settings")
                OutlinedTextField(
                    value = config.publicKey,
                    onValueChange = { viewModel.updatePublicKey(it) },
                    label = { Text("Public Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.shortId,
                    onValueChange = { viewModel.updateShortId(it) },
                    label = { Text("Short ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.spiderX,
                    onValueChange = { viewModel.updateSpiderX(it) },
                    label = { Text("Spider X") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // WebSocket settings
            if (config.network == TransportType.WS) {
                SectionHeader("WebSocket Settings")
                OutlinedTextField(
                    value = config.wsPath,
                    onValueChange = { viewModel.updateWsPath(it) },
                    label = { Text("Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.wsHost,
                    onValueChange = { viewModel.updateWsHost(it) },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // gRPC settings
            if (config.network == TransportType.GRPC) {
                SectionHeader("gRPC Settings")
                OutlinedTextField(
                    value = config.grpcServiceName,
                    onValueChange = { viewModel.updateGrpcServiceName(it) },
                    label = { Text("Service Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom padding for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
