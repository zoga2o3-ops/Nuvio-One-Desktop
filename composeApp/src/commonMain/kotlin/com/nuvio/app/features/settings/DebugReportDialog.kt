package com.nuvio.app.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.diagnostics.DebugReportHost
import com.nuvio.app.core.diagnostics.DebugReportReceipt
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.debug_report_dialog_body
import nuvio.composeapp.generated.resources.debug_report_dialog_title
import nuvio.composeapp.generated.resources.debug_report_failed
import nuvio.composeapp.generated.resources.debug_report_saved
import nuvio.composeapp.generated.resources.debug_report_send
import nuvio.composeapp.generated.resources.debug_report_sending
import nuvio.composeapp.generated.resources.debug_report_sent
import org.jetbrains.compose.resources.stringResource

/** Sends the redacted desktop debug report and shows its reference number. */
@Composable
internal fun DesktopDebugReportDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<DebugReportReceipt?>(null) }
    var failed by remember { mutableStateOf(false) }

    DialogSurface(
        onDismissRequest = { if (!sending) onDismiss() },
        title = stringResource(Res.string.debug_report_dialog_title),
    ) {
        Text(
            text = when {
                receipt?.sent == true -> stringResource(Res.string.debug_report_sent, receipt!!.id)
                receipt != null -> stringResource(Res.string.debug_report_saved, receipt!!.id)
                failed -> stringResource(Res.string.debug_report_failed)
                sending -> stringResource(Res.string.debug_report_sending)
                else -> stringResource(Res.string.debug_report_dialog_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DialogButtons {
            if (receipt != null) {
                DialogButton(
                    text = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                    style = DialogButtonStyle.Primary,
                )
            } else {
                DialogButton(
                    text = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                    enabled = !sending,
                )
                DialogButton(
                    text = stringResource(Res.string.debug_report_send),
                    onClick = {
                        if (sending) return@DialogButton
                        sending = true
                        failed = false
                        scope.launch {
                            val result = DebugReportHost.send?.invoke()
                            sending = false
                            if (result == null) {
                                failed = true
                            } else {
                                result.fold(
                                    onSuccess = {
                                        receipt = it
                                        failed = false
                                    },
                                    onFailure = { failed = true },
                                )
                            }
                        }
                    },
                    style = DialogButtonStyle.Primary,
                    enabled = !sending,
                    loading = sending,
                )
            }
        }
    }
}
