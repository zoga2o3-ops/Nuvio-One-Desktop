package com.nuvio.app.features.iptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The SMB share and the ffmpeg used for combines and MP4 exports. */
@Composable
internal fun IptvRecordingSettingsDialog(
    onDismiss: () -> Unit,
    onSaved: (IptvSmbConfig?) -> Unit,
) {
    val current = remember { IptvSmbStore.load() }
    var host by remember { mutableStateOf(current?.host.orEmpty()) }
    var port by remember { mutableStateOf((current?.port ?: 445).toString()) }
    var share by remember { mutableStateOf(current?.share.orEmpty()) }
    var folder by remember { mutableStateOf(current?.folder.orEmpty()) }
    var domain by remember { mutableStateOf(current?.domain.orEmpty()) }
    var username by remember { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf(current?.password.orEmpty()) }
    var ffmpeg by remember { mutableStateOf(IptvExportSettings.ffmpegPath().orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun config(): IptvSmbConfig? {
        if (host.isBlank() || share.isBlank()) return null
        return IptvSmbConfig(
            host = host.trim(),
            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 445,
            share = share.trim(),
            folder = folder.trim(),
            domain = domain.trim(),
            username = username.trim(),
            password = password,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recordings settings") },
        text = {
            Column(
                Modifier.width(480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("SMB share", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Recordings made by the TV and phone appear here, and combines and exports are saved back to it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(host, { host = it }, label = { Text("Server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.width(110.dp))
                    OutlinedTextField(share, { share = it }, label = { Text("Share") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(folder, { folder = it }, label = { Text("Folder (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !testing && config() != null,
                        onClick = {
                            val target = config() ?: return@TextButton
                            testing = true
                            testResult = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { IptvSmbClient.test(target) }
                                testResult = result.fold(
                                    onSuccess = { free ->
                                        "Connected" + if (free > 0) " - ${sizeLabel(free)} free" else ""
                                    },
                                    onFailure = { "Could not connect: ${it.message ?: it.javaClass.simpleName}" },
                                )
                                testing = false
                            }
                        },
                    ) { Text(if (testing) "Testing..." else "Test") }
                    if (current != null) TextButton(onClick = {
                        IptvSmbStore.clear()
                        onSaved(null)
                        onDismiss()
                    }) { Text("Remove share") }
                }
                testResult?.let { result ->
                    Text(result, style = MaterialTheme.typography.bodySmall)
                }

                Text("MP4 export", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    ffmpeg,
                    { ffmpeg = it },
                    label = { Text("ffmpeg path (optional)") },
                    placeholder = { Text("Leave empty to use ffmpeg from PATH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    IptvExportSettings.findFfmpeg()?.let { "Found: $it" }
                        ?: "ffmpeg wasn't found. Install it or point to ffmpeg.exe above.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = config()
                if (target != null) IptvSmbStore.save(target) else IptvSmbStore.clear()
                IptvExportSettings.setFfmpegPath(ffmpeg)
                onSaved(target)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1024} KB"
}
