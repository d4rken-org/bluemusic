package eu.darken.bluemusic.common.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_review_gplay")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // onErrorFallbackToDefault is off: corrupt data has to surface instead of silently resetting the
    // snooze/reviewed bookkeeping to "never".
    val lastDismissed = dataStore.createValue<Instant?>(
        "review.dismissedAt", null, json,
        onErrorFallbackToDefault = false,
    )
    val reviewedAt = dataStore.createValue<Instant?>(
        "review.reviewedAt", null, json,
        onErrorFallbackToDefault = false,
    )

    companion object {
        internal val TAG = logTag("Review", "Settings", "Gplay")
    }
}
