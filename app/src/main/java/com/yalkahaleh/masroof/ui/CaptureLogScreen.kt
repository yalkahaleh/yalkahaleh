package com.yalkahaleh.masroof.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yalkahaleh.masroof.capture.LogExport
import com.yalkahaleh.masroof.capture.Prefs
import com.yalkahaleh.masroof.capture.SmsInboxScanner
import com.yalkahaleh.masroof.data.AppDatabase
import com.yalkahaleh.masroof.data.CapturedNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureLogScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).capturedNotifications() }
    val items by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var accessGranted by remember { mutableStateOf(hasNotificationAccess(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    var captureAll by remember { mutableStateOf(Prefs.captureAll(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    // Re-check when the user comes back from the system settings screens.
    LifecycleResumeEffect(Unit) {
        accessGranted = hasNotificationAccess(context)
        batteryUnrestricted = isBatteryUnrestricted(context)
        onPauseOrDispose { }
    }

    val saveLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val json = LogExport.toJson(dao.getAll())
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "Log saved" else "Could not save the log", Toast.LENGTH_LONG).show()
        }
    }

    fun scanSms() = scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { SmsInboxScanner.scan(context, captureAll) } }
        val message = result.fold(
            onSuccess = { "Read ${it.read} SMS from the last 30 days, added ${it.added}" },
            onFailure = { "Could not read SMS: ${it.message}" },
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanSms()
        else Toast.makeText(
            context,
            "SMS permission denied. Allow it in App info > Permissions, then try again.",
            Toast.LENGTH_LONG,
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment capture test") },
                actions = {
                    TextButton(enabled = items.isNotEmpty(), onClick = { confirmClear = true }) { Text("Clear") }
                    TextButton(enabled = items.isNotEmpty(), onClick = { saveLog.launch("masroof-log.json") }) {
                        Text("Export")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!accessGranted) item {
                SetupCard(
                    title = "Step 1: allow notification access",
                    body = "Masroof needs this to see payment notifications. If Android says " +
                        "\"Restricted setting\", open App info for Masroof, tap the ⋮ menu, choose " +
                        "\"Allow restricted settings\", then try again.",
                    primary = "Open notification access",
                    onPrimary = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    secondary = "App info",
                    onSecondary = { openAppInfo(context) },
                )
            }
            if (!batteryUnrestricted) item {
                SetupCard(
                    title = "Step 2: stop battery saving from pausing capture",
                    body = "Without this, Samsung may stop Masroof in the background and payments will be missed.",
                    primary = "Allow background use",
                    onPrimary = { requestBatteryExemption(context) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Capture every app", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Off: only wallet apps, SMS, and messages that mention money. " +
                                "Turn on if a payment did not show up below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = captureAll, onCheckedChange = {
                        captureAll = it
                        Prefs.setCaptureAll(context, it)
                    })
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { askSms.launch(Manifest.permission.READ_SMS) }) {
                        Text("Scan SMS inbox")
                    }
                    Text(
                        "Reads text messages already on the phone from the last 30 days and adds the " +
                            "ones that mention money. Use this if your bank sends an SMS per payment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    if (items.isEmpty()) {
                        if (accessGranted) "Listening. Make a payment and its notification will appear here."
                        else "Nothing captured yet."
                    } else "${items.size} captured",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items, key = { it.id }) { item ->
                NotificationCard(item, onDelete = { scope.launch { dao.delete(item.id) } })
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear the log?") },
        text = { Text("All ${items.size} captured notifications will be deleted.") },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; scope.launch { dao.clear() } }) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SetupCard(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { Text(primary) }
                if (secondary != null) OutlinedButton(onClick = onSecondary) { Text(secondary) }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: CapturedNotification, onDelete: () -> Unit) {
    val time = remember(item.postedAt) {
        SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(item.postedAt))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Text(
                "${item.appLabel} · $time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(item.packageName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.title.isNotBlank()) Text(item.title, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp, end = 12.dp))
            val body = if (item.bigText.isBlank() || item.bigText.startsWith("--- raw ---")) listOf(item.text, item.bigText).filter { it.isNotBlank() }.joinToString("\n") else item.bigText
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp))
            if (item.subText.isNotBlank()) Text(item.subText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("Delete") }
        }
    }
}

private fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}

@SuppressLint("BatteryLife") // Personal, directly-installed app: continuous capture is its whole purpose.
private fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    }.onFailure { openAppInfo(context) }
}
