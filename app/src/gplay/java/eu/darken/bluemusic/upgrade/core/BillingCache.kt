package eu.darken.bluemusic.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_gplay")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastProStateAt = dataStore.createValue("gplay.cache.lastProAt", 0L)
    val lastProStateSku = dataStore.createValue("gplay.cache.lastProSku", "")

    // Both values describe one fact — "when were we last Pro, and via which SKU" — so they are
    // written in a single DataStore transaction: a process death can't leave a fresh timestamp
    // paired with a stale SKU (which would select the wrong grace window).
    suspend fun stampLastProState(skuId: String, at: Long) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                lastProStateSku.setIn(this, skuId)
                lastProStateAt.setIn(this, at)
            }.toPreferences()
        }
    }
}
