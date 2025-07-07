package eu.darken.bluemusic.common.settings

import eu.darken.bluemusic.common.ca.CaString

interface EnumPreference<T : Enum<T>> {
    val label: CaString
}