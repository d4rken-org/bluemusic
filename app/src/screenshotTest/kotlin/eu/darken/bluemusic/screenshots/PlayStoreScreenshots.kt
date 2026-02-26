package eu.darken.bluemusic.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PlayStoreLocales
@Composable
fun DashboardLight() = DashboardContent()

@PreviewTest
@PlayStoreLocalesDark
@Composable
fun DashboardDark() = DashboardContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun DeviceConfigTop() = DeviceConfigTopContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun DeviceConfigReaction() = DeviceConfigReactionContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun DeviceConfigTiming() = DeviceConfigTimingContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun AppLauncher() = AppSelectionContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Autoplay() = AutoplayContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Settings() = SettingsContent()
