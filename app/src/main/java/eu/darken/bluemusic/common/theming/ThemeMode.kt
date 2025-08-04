package eu.darken.bluemusic.common.theming

import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.CaString
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.settings.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(override val label: CaString) : EnumPreference<ThemeMode> {
    @SerialName("SYSTEM") SYSTEM(R.string.ui_theme_mode_system_label.toCaString()),
    @SerialName("DARK") DARK(R.string.ui_theme_mode_dark_label.toCaString()),
    @SerialName("LIGHT") LIGHT(R.string.ui_theme_mode_light_label.toCaString()),
}

