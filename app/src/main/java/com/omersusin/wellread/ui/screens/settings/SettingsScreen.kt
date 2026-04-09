package com.omersusin.wellread.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.omersusin.wellread.ui.screens.reader.modeColor
import com.omersusin.wellread.ui.screens.reader.modeIcon
import com.omersusin.wellread.ui.screens.reader.modeLabel
import com.omersusin.wellread.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style     = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
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
            // ── Appearance ──────────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Outlined.Palette, title = "Appearance")
            SettingsCard {
                SwitchSettingRow(
                    icon     = Icons.Outlined.DarkMode,
                    title    = "Dark Mode",
                    subtitle = "Use dark colour scheme",
                    checked  = prefs.isDarkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                SwitchSettingRow(
                    icon     = Icons.Outlined.Palette,
                    title    = "Dynamic Color",
                    subtitle = "Adapt colours to your wallpaper",
                    checked  = prefs.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            // ── Reading ─────────────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Outlined.AutoStories, title = "Reading")
            SettingsCard {
                SliderSettingRow(
                    icon       = Icons.Outlined.Speed,
                    title      = "Default Speed",
                    subtitle   = "${prefs.defaultWpm} WPM",
                    value      = prefs.defaultWpm.toFloat(),
                    valueRange = 50f..800f,
                    onValueChange = { viewModel.setDefaultWpm(it.toInt()) },
                    color      = FlashColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                SliderSettingRow(
                    icon       = Icons.Outlined.TextFields,
                    title      = "Font Size",
                    subtitle   = "${prefs.fontSize.toInt()} sp",
                    value      = prefs.fontSize,
                    valueRange = 12f..32f,
                    onValueChange = viewModel::setFontSize,
                    color      = WellReadPurple
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                SliderSettingRow(
                    icon       = Icons.Outlined.FormatBold,
                    title      = "Bionic Fixation Strength",
                    subtitle   = "${(prefs.bionicFixationStrength * 100).toInt()}%",
                    value      = prefs.bionicFixationStrength,
                    valueRange = 0.2f..0.8f,
                    onValueChange = viewModel::setBionicStrength,
                    color      = BionicColor
                )
            }

            // ── Default Mode ────────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Outlined.Bolt, title = "Default Reading Mode")
            SettingsCard {
                ReadingMode.values().forEachIndexed { index, mode ->
                    ModeSelectionRow(
                        mode     = mode,
                        selected = prefs.defaultMode == mode,
                        onClick  = { viewModel.setDefaultMode(mode) }
                    )
                    if (index < ReadingMode.values().size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    }
                }
            }

            // ── Goals ───────────────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Outlined.TrackChanges, title = "Goals")
            SettingsCard {
                SliderSettingRow(
                    icon       = Icons.Outlined.Timer,
                    title      = "Daily Reading Goal",
                    subtitle   = "${prefs.dailyGoalMinutes} minutes",
                    value      = prefs.dailyGoalMinutes.toFloat(),
                    valueRange = 5f..120f,
                    onValueChange = { viewModel.setDailyGoal(it.toInt()) },
                    color      = FocusColor
                )
            }

            // ── About ───────────────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Outlined.Info, title = "About")
            SettingsCard {
                Row(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape    = RoundedCornerShape(14.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Outlined.AutoStories, null,
                                modifier = Modifier.size(24.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            "WellRead",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Version 1.0.0 — Smart Speed Reader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier            = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 2.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(16.dp),
            tint     = MaterialTheme.colorScheme.primary
        )
        Text(
            text       = title,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(22.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
        modifier  = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape    = RoundedCornerShape(10.dp),
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, null,
                    modifier = Modifier.size(20.dp),
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title,   style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = color.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title,    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value      = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier   = Modifier.fillMaxWidth(),
            colors     = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun ModeSelectionRow(
    mode: ReadingMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = mode.modeColor()
    Row(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape    = RoundedCornerShape(10.dp),
            color    = color.copy(alpha = 0.15f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(mode.modeIcon(), null, modifier = Modifier.size(20.dp), tint = color)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mode.modeLabel(),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (selected) {
            Icon(
                Icons.Default.CheckCircle, null,
                tint     = color,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
