package com.omersusin.wellread.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.screens.reader.modeEmoji
import com.omersusin.wellread.ui.screens.reader.modeLabel
import com.omersusin.wellread.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Appearance
            SettingsSectionHeader("🎨 Appearance")
            SettingsCard {
                SwitchSettingRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    checked = prefs.isDarkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                SwitchSettingRow(
                    icon = Icons.Outlined.Palette,
                    title = "Dynamic Color",
                    subtitle = "Match your wallpaper colors",
                    checked = prefs.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            // Reading
            SettingsSectionHeader("📖 Reading")
            SettingsCard {
                SliderSettingRow(
                    icon = Icons.Outlined.Speed,
                    title = "Default Speed",
                    subtitle = "${prefs.defaultWpm} WPM",
                    value = prefs.defaultWpm.toFloat(),
                    valueRange = 50f..800f,
                    onValueChange = { viewModel.setDefaultWpm(it.toInt()) },
                    color = FlashColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                SliderSettingRow(
                    icon = Icons.Outlined.TextFields,
                    title = "Font Size",
                    subtitle = "${prefs.fontSize.toInt()}sp",
                    value = prefs.fontSize,
                    valueRange = 12f..32f,
                    onValueChange = viewModel::setFontSize,
                    color = WellReadPurple
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                SliderSettingRow(
                    icon = Icons.Outlined.FormatBold,
                    title = "Bionic Strength",
                    subtitle = "${(prefs.bionicFixationStrength * 100).toInt()}%",
                    value = prefs.bionicFixationStrength,
                    valueRange = 0.2f..0.8f,
                    onValueChange = viewModel::setBionicStrength,
                    color = BionicColor
                )
            }

            // Default Mode
            SettingsSectionHeader("⚡ Default Mode")
            SettingsCard {
                ReadingMode.values().forEachIndexed { index, mode ->
                    ModeSelectionRow(
                        emoji = mode.modeEmoji(),
                        label = mode.modeLabel(),
                        selected = prefs.defaultMode == mode,
                        color = when (mode) {
                            ReadingMode.BIONIC -> BionicColor
                            ReadingMode.FLASH -> FlashColor
                            ReadingMode.FOCUS -> FocusColor
                            ReadingMode.TRAIN -> TrainColor
                            ReadingMode.SENTENCE_SWIPE -> SwipeColor
                        },
                        onClick = { viewModel.setDefaultMode(mode) }
                    )
                    if (index < ReadingMode.values().size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    }
                }
            }

            // Goals
            SettingsSectionHeader("🎯 Goals")
            SettingsCard {
                SliderSettingRow(
                    icon = Icons.Outlined.Timer,
                    title = "Daily Goal",
                    subtitle = "${prefs.dailyGoalMinutes} minutes",
                    value = prefs.dailyGoalMinutes.toFloat(),
                    valueRange = 5f..120f,
                    onValueChange = { viewModel.setDailyGoal(it.toInt()) },
                    color = FocusColor
                )
            }

            // About
            SettingsSectionHeader("ℹ️ About")
            SettingsCard {
                AboutRow(title = "WellRead", subtitle = "Version 1.0.0 · Smart Reading App")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = WellReadPurple, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = WellReadPurple)
        )
    }
}

@Composable
private fun SliderSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun ModeSelectionRow(
    emoji: String,
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AboutRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📖", fontSize = 24.sp)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
