package eu.darken.bluemusic.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.createValue
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_foss")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val upgrade = dataStore.createValue<FossUpgrade?>(
        key = "foss.upgrade",
        json = json,
        defaultValue = null,
    )

}