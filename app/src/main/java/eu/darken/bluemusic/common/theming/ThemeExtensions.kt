package eu.darken.bluemusic.common.theming

import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.common.theming.ThemeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val GeneralSettings.themeState: Flow<ThemeState>
    get() = combine(
        themeMode.flow,
        themeStyle.flow
    ) { mode, style ->
        ThemeState(mode, style)
    }