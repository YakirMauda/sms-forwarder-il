package com.qrcommunication.smsforwarder.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.ui.common.SectionCard

/**
 * Shows [content] only once RECEIVE_SMS + SEND_SMS are granted.
 *
 * If Android refuses without showing a dialog (permanently denied, or blocked as a
 * "restricted setting" for sideloaded apps on Android 13+), it explains how to allow it
 * from App info. The state is re-checked every time the app comes back to the foreground,
 * so returning from Settings unlocks the app immediately.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(SystemState.hasSmsPermissions(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        granted = SystemState.hasSmsPermissions(context)
        if (granted) blocked = false
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = SystemState.hasSmsPermissions(context)
        askedOnce = true
        val activity = context.findActivity()
        // Denied and Android won't ask again (or never showed the dialog) -> manual path.
        blocked = !granted && activity != null && SystemState.SMS_PERMISSIONS.none {
            activity.shouldShowRequestPermissionRationale(it)
        }
    }

    if (granted) {
        content()
        return
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.perm_text), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { launcher.launch(SystemState.SMS_PERMISSIONS + SystemState.OPTIONAL_PERMISSIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.perm_grant)) }

        if (blocked || askedOnce) {
            SectionCard {
                Text(stringResource(R.string.perm_denied_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.perm_denied_text), style = MaterialTheme.typography.bodyMedium)
                RestrictedSettingsSteps()
                Button(
                    onClick = { context.startActivity(SystemState.appDetailsIntent(context)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(R.string.action_open_app_info)) }
            }
        }
    }
}

/**
 * Step-by-step "Allow restricted settings" guide. The text comes from the current locale,
 * so in Hebrew it says the ⋮ menu is at the top-left (RTL) and in English at the top-right.
 */
@Composable
fun RestrictedSettingsSteps() {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.restricted_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        listOf(R.string.restricted_step1, R.string.restricted_step2, R.string.restricted_step3, R.string.restricted_step4)
            .forEachIndexed { i, res ->
                Row {
                    Text("${i + 1}. ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
    }
}

/** Orange strip shown on every tab while the app is still under battery optimization. */
@Composable
fun BatteryBanner(onFix: () -> Unit) {
    SectionCard(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_banner_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.battery_banner_text), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
        }
    }
}

/** Explains why, then opens Android's own "ignore battery optimizations" prompt. */
@Composable
fun BatteryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_dialog_title)) },
        text = { Text(stringResource(R.string.battery_dialog_text)) },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                runCatching { context.startActivity(SystemState.batteryExemptionIntent(context)) }
                    .onFailure { context.startActivity(SystemState.appDetailsIntent(context)) }
            }) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
