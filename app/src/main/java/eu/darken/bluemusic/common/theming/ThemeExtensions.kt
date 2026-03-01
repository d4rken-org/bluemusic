package eu.darken.bluemusic.common.theming

import eu.darken.bluemusic.main.core.GeneralSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val GeneralSettings.themeState: Flow<ThemeState>
    get() = combine(
        themeMode.flow,
        themeStyle.flow,
        themeColor.flow,
    ) { mode, style, color ->
        ThemeState(mode = mode, style = style, color = color)
    }