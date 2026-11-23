package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.UserSettings
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.components.DropLinkCard
import com.example.ui.theme.LocalDropLinkColors
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDeviceNameSave: (String) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    onAutoAcceptTrustedChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSaveFolderSelected: (Uri?) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val context = LocalContext.current

    var deviceNameInput by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onSaveFolderSelected(uri)
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsChange(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Appearance
            DropLinkCard {
                Text(s.appearance, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.DARK to s.themeDark,
                        ThemeMode.LIGHT to s.themeLight,
                        ThemeMode.SYSTEM to s.themeSystem
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Language
            DropLinkCard {
                Text(s.languageLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.language == AppLanguage.AR,
                        onClick = { onLanguageChange(AppLanguage.AR) },
                        label = { Text("العربية") },
                        modifier = Modifier.testTag("lang_ar_chip")
                    )
                    FilterChip(
                        selected = settings.language == AppLanguage.EN,
                        onClick = { onLanguageChange(AppLanguage.EN) },
                        label = { Text("English") },
                        modifier = Modifier.testTag("lang_en_chip")
                    )
                }
            }

            // Device Identity
            DropLinkCard {
                Text(s.deviceIdentity, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = deviceNameInput,
                        onValueChange = { deviceNameInput = it },
                        label = { Text(s.deviceNameLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onDeviceNameSave(deviceNameInput) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(s.save)
                    }
                }
            }

            // Receive & Notification Preferences
            DropLinkCard {
                Text(s.receiveSettings, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = s.requireApprovalLabel,
                    subtitle = s.requireApprovalDesc,
                    checked = settings.requireApproval,
                    onCheckedChange = onRequireApprovalChange
                )
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = s.autoAcceptTrustedLabel,
                    subtitle = s.autoAcceptTrustedDesc,
                    checked = settings.autoAcceptTrusted,
                    onCheckedChange = onAutoAcceptTrustedChange
                )
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = s.notificationsLabel,
                    subtitle = s.notificationsDesc,
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { wantEnable ->
                        if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onNotificationsChange(true)
                            }
                        } else {
                            onNotificationsChange(wantEnable)
                        }
                    }
                )
            }

            // Default Save Folder
            DropLinkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = s.saveFolderLabel, tint = ext.cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.saveFolderLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = settings.customSaveFolderLabel ?: s.defaultDownloadsFolder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(s.changeFolder)
                    }
                    if (settings.customSaveTreeUri != null) {
                        OutlinedButton(
                            onClick = { onSaveFolderSelected(null) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(s.resetDefault)
                        }
                    }
                }
            }

            // Links to Privacy & About
            DropLinkCard(onClick = onOpenPrivacy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PrivacyTip, contentDescription = s.privacySettings, tint = ext.cyan)
                    Spacer(Modifier.width(12.dp))
                    Text(s.privacySettings, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }

            DropLinkCard(onClick = onOpenAbout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = s.aboutDropLink, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(s.aboutDropLink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
