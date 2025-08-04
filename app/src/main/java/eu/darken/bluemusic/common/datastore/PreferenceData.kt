package eu.darken.bluemusic.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

interface PreferenceData {

    val dataStore: DataStore<Preferences>

}