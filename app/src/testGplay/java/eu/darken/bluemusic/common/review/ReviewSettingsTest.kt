package eu.darken.bluemusic.common.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReviewSettingsTest : BaseTest() {

    // The app's Json, not a bare `Json {}`: `java.time.Instant` is only serializable here through
    // the contextual InstantSerializer this module registers.
    private val json = SerializationModule().json()

    // One test method on purpose: ReviewSettings is a @Singleton whose DataStore is bound to the
    // Context property delegate, and DataStore forbids two active instances on the same file.
    @Test
    fun `the review timestamps round-trip through the real DataStore`() = runTest {
        // Real time and real I/O: the DataStore does its work off the test scheduler.
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Real DataStore, no mocks: this catches a mismatch between what the Instant serializer
            // writes and what the reader expects to find.
            val settings = ReviewSettings(context, json)

            settings.lastDismissed.value() shouldBe null
            settings.reviewedAt.value() shouldBe null

            // ISO-8601 with nanosecond precision, nothing is truncated on the way through.
            val dismissedAt = Instant.parse("2023-11-14T22:13:20.000000001Z")
            val reviewedAt = Instant.parse("2023-11-14T22:15:23.456789123Z")

            settings.lastDismissed.value(dismissedAt)
            settings.reviewedAt.value(reviewedAt)

            settings.lastDismissed.value() shouldBe dismissedAt
            settings.reviewedAt.value() shouldBe reviewedAt

            // Writing null clears the key instead of storing a literal "null" that would then be
            // decoded on the next read.
            settings.lastDismissed.value(null)
            settings.lastDismissed.value() shouldBe null
            settings.dataStore.data.first().contains(DISMISSED_KEY) shouldBe false

            // onErrorFallbackToDefault is off, so corrupt data surfaces instead of silently
            // resetting the snooze/reviewed bookkeeping to "never".
            settings.dataStore.edit { it[REVIEWED_KEY] = "not-a-timestamp" }
            shouldThrow<SerializationException> { settings.reviewedAt.value() }
        }
    }

    companion object {
        private val DISMISSED_KEY = stringPreferencesKey("review.dismissedAt")
        private val REVIEWED_KEY = stringPreferencesKey("review.reviewedAt")
    }
}
