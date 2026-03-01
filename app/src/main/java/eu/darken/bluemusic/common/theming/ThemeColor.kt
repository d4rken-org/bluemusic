package eu.darken.bluemusic.common.theming

import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.CaString
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.settings.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(override val label: CaString) : EnumPreference<ThemeColor> {
    @SerialName("BLUE") BLUE(R.string.ui_theme_color_blue_label.toCaString()),
    @SerialName("SUNSET") SUNSET(R.string.ui_theme_color_sunset_label.toCaString()),
}
