package eu.darken.bluemusic.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.basicReader
import eu.darken.bluemusic.common.datastore.basicWriter
import eu.darken.bluemusic.common.datastore.createValue
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_gplay")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // Raw keys shared between the DataStoreValues and the snapshot read — one source of truth for
    // key name and encoding.
    private val lastProStateAtKey = longPreferencesKey("gplay.cache.lastProAt")
    private val lastProStateSkuKey = stringPreferencesKey("gplay.cache.lastProSku")

    val lastProStateAt = dataStore.createValue(
        key = lastProStateAtKey,
        reader = basicReader(0L),
        writer = basicWriter(),
    )
    val lastProStateSku = dataStore.createValue(
        key = lastProStateSkuKey,
        reader = basicReader(""),
        writer = basicWriter(),
    )

    // Point-in-time view of the cached values. Reading them via separate .value() calls can
    // straddle a concurrent stampLastProState() and observe a combination that never existed --
    // that write is transactional precisely because the values are only meaningful together.
    data class Snapshot(
        val lastProStateAt: Long,
        val lastProStateSku: String,
        val proUnconfirmedSince: Long,
    )

    suspend fun snapshot(): Snapshot {
        val prefs = dataStore.data.first()
        return Snapshot(
            lastProStateAt = prefs[lastProStateAtKey] ?: 0L,
            lastProStateSku = prefs[lastProStateSkuKey] ?: "",
            // The current entitlement layer has no "fresh data can't confirm Pro" episode tracking,
            // so there is nothing persisted to read: always reported as "no open episode".
            proUnconfirmedSince = 0L,
        )
    }

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
