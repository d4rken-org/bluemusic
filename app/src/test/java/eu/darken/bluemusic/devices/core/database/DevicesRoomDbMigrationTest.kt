package eu.darken.bluemusic.devices.core.database

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the auto-migrations that introduce the equalizer columns against the exported schemas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class DevicesRoomDbMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DevicesRoomDb::class.java,
    )

    @Test
    fun `migrate 4 to 5 adds equalizer columns and keeps existing config`() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO device_configs (
                    address, custom_name, last_connected, action_delay, adjustment_delay, monitoring_duration,
                    music_volume, call_volume, ring_volume, notification_volume, alarm_volume,
                    volume_lock, volume_observing, volume_rate_limiter,
                    volume_rate_limit_increase_ms, volume_rate_limit_decrease_ms, volume_save_on_disconnect,
                    keep_awake, nudge_volume, autoplay, launch_pkgs, show_home_screen, autoplay_keycodes,
                    is_enabled, visible_adjustments, dnd_mode, connection_alert_type, connection_alert_sound_uri
                ) VALUES (
                    'AA:BB:CC:DD:EE:FF', 'My Headphones', 1713270600000, 500, 200, 30000,
                    0.75, 0.5, 0.3, 0.4, 0.6,
                    1, 1, 1,
                    100, 200, 1,
                    1, 1, 1, '["com.spotify.music"]', 1, '[126,85]',
                    1, 0, 'priority_only', 'sound', 'content://media/external/audio/123'
                )
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 5, true)

        migrated.query("SELECT * FROM device_configs").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.count shouldBe 1

            cursor.getInt(cursor.getColumnIndexOrThrow("eq_enabled")) shouldBe 0
            cursor.isNull(cursor.getColumnIndexOrThrow("eq_band_levels")) shouldBe true

            cursor.getString(cursor.getColumnIndexOrThrow("address")) shouldBe "AA:BB:CC:DD:EE:FF"
            cursor.getString(cursor.getColumnIndexOrThrow("custom_name")) shouldBe "My Headphones"
            cursor.getLong(cursor.getColumnIndexOrThrow("last_connected")) shouldBe 1713270600000L
            cursor.getLong(cursor.getColumnIndexOrThrow("action_delay")) shouldBe 500L
            cursor.getLong(cursor.getColumnIndexOrThrow("adjustment_delay")) shouldBe 200L
            cursor.getLong(cursor.getColumnIndexOrThrow("monitoring_duration")) shouldBe 30000L
            cursor.getFloat(cursor.getColumnIndexOrThrow("music_volume")) shouldBe 0.75f
            cursor.getFloat(cursor.getColumnIndexOrThrow("call_volume")) shouldBe 0.5f
            cursor.getFloat(cursor.getColumnIndexOrThrow("ring_volume")) shouldBe 0.3f
            cursor.getFloat(cursor.getColumnIndexOrThrow("notification_volume")) shouldBe 0.4f
            cursor.getFloat(cursor.getColumnIndexOrThrow("alarm_volume")) shouldBe 0.6f
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_lock")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_observing")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_rate_limiter")) shouldBe 1
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_increase_ms")) shouldBe 100L
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_decrease_ms")) shouldBe 200L
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_save_on_disconnect")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("keep_awake")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("nudge_volume")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("autoplay")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("launch_pkgs")) shouldBe """["com.spotify.music"]"""
            cursor.getInt(cursor.getColumnIndexOrThrow("show_home_screen")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("autoplay_keycodes")) shouldBe "[126,85]"
            cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("visible_adjustments")) shouldBe 0
            cursor.getString(cursor.getColumnIndexOrThrow("dnd_mode")) shouldBe "priority_only"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_type")) shouldBe "sound"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_sound_uri")) shouldBe
                    "content://media/external/audio/123"
        }
    }

    @Test
    fun `migrate 5 to 6 adds the boost column and keeps existing config`() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO device_configs (
                    address, custom_name, last_connected, action_delay, adjustment_delay, monitoring_duration,
                    music_volume, call_volume, ring_volume, notification_volume, alarm_volume,
                    volume_lock, volume_observing, volume_rate_limiter,
                    volume_rate_limit_increase_ms, volume_rate_limit_decrease_ms, volume_save_on_disconnect,
                    keep_awake, nudge_volume, autoplay, launch_pkgs, show_home_screen, autoplay_keycodes,
                    is_enabled, visible_adjustments, dnd_mode, connection_alert_type, connection_alert_sound_uri,
                    eq_enabled, eq_band_levels
                ) VALUES (
                    'AA:BB:CC:DD:EE:FF', 'My Headphones', 1713270600000, 500, 200, 30000,
                    0.75, 0.5, 0.3, 0.4, 0.6,
                    1, 1, 1,
                    100, 200, 1,
                    1, 1, 1, '["com.spotify.music"]', 1, '[126,85]',
                    1, 0, 'priority_only', 'sound', 'content://media/external/audio/123',
                    1, '[300,0,-300]'
                )
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 6, true)

        migrated.query("SELECT * FROM device_configs").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.count shouldBe 1

            cursor.isNull(cursor.getColumnIndexOrThrow("eq_boost_gain")) shouldBe true

            cursor.getInt(cursor.getColumnIndexOrThrow("eq_enabled")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("eq_band_levels")) shouldBe "[300,0,-300]"

            cursor.getString(cursor.getColumnIndexOrThrow("address")) shouldBe "AA:BB:CC:DD:EE:FF"
            cursor.getString(cursor.getColumnIndexOrThrow("custom_name")) shouldBe "My Headphones"
            cursor.getLong(cursor.getColumnIndexOrThrow("last_connected")) shouldBe 1713270600000L
            cursor.getLong(cursor.getColumnIndexOrThrow("action_delay")) shouldBe 500L
            cursor.getLong(cursor.getColumnIndexOrThrow("adjustment_delay")) shouldBe 200L
            cursor.getLong(cursor.getColumnIndexOrThrow("monitoring_duration")) shouldBe 30000L
            cursor.getFloat(cursor.getColumnIndexOrThrow("music_volume")) shouldBe 0.75f
            cursor.getFloat(cursor.getColumnIndexOrThrow("call_volume")) shouldBe 0.5f
            cursor.getFloat(cursor.getColumnIndexOrThrow("ring_volume")) shouldBe 0.3f
            cursor.getFloat(cursor.getColumnIndexOrThrow("notification_volume")) shouldBe 0.4f
            cursor.getFloat(cursor.getColumnIndexOrThrow("alarm_volume")) shouldBe 0.6f
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_lock")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_observing")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_rate_limiter")) shouldBe 1
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_increase_ms")) shouldBe 100L
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_decrease_ms")) shouldBe 200L
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_save_on_disconnect")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("keep_awake")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("nudge_volume")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("autoplay")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("launch_pkgs")) shouldBe """["com.spotify.music"]"""
            cursor.getInt(cursor.getColumnIndexOrThrow("show_home_screen")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("autoplay_keycodes")) shouldBe "[126,85]"
            cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("visible_adjustments")) shouldBe 0
            cursor.getString(cursor.getColumnIndexOrThrow("dnd_mode")) shouldBe "priority_only"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_type")) shouldBe "sound"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_sound_uri")) shouldBe
                    "content://media/external/audio/123"
        }
    }

    @Test
    fun `migrate 6 to 7 adds the volume limit columns and keeps existing config`() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO device_configs (
                    address, custom_name, last_connected, action_delay, adjustment_delay, monitoring_duration,
                    music_volume, call_volume, ring_volume, notification_volume, alarm_volume,
                    volume_lock, volume_observing, volume_rate_limiter,
                    volume_rate_limit_increase_ms, volume_rate_limit_decrease_ms, volume_save_on_disconnect,
                    keep_awake, nudge_volume, autoplay, launch_pkgs, show_home_screen, autoplay_keycodes,
                    is_enabled, visible_adjustments, dnd_mode, connection_alert_type, connection_alert_sound_uri,
                    eq_enabled, eq_band_levels, eq_boost_gain
                ) VALUES (
                    'AA:BB:CC:DD:EE:FF', 'My Headphones', 1713270600000, 500, 200, 30000,
                    0.75, 0.5, 0.3, 0.4, 0.6,
                    1, 1, 1,
                    100, 200, 1,
                    1, 1, 1, '["com.spotify.music"]', 1, '[126,85]',
                    1, 0, 'priority_only', 'sound', 'content://media/external/audio/123',
                    1, '[300,0,-300]', 400
                )
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 7, true)

        migrated.query("SELECT * FROM device_configs").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.count shouldBe 1

            cursor.getInt(cursor.getColumnIndexOrThrow("volume_limit")) shouldBe 0
            for (column in listOf(
                "music_volume_min", "music_volume_max",
                "call_volume_min", "call_volume_max",
                "ring_volume_min", "ring_volume_max",
                "notification_volume_min", "notification_volume_max",
                "alarm_volume_min", "alarm_volume_max",
            )) {
                cursor.isNull(cursor.getColumnIndexOrThrow(column)) shouldBe true
            }

            cursor.getInt(cursor.getColumnIndexOrThrow("eq_enabled")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("eq_band_levels")) shouldBe "[300,0,-300]"
            cursor.getInt(cursor.getColumnIndexOrThrow("eq_boost_gain")) shouldBe 400

            cursor.getString(cursor.getColumnIndexOrThrow("address")) shouldBe "AA:BB:CC:DD:EE:FF"
            cursor.getString(cursor.getColumnIndexOrThrow("custom_name")) shouldBe "My Headphones"
            cursor.getLong(cursor.getColumnIndexOrThrow("last_connected")) shouldBe 1713270600000L
            cursor.getLong(cursor.getColumnIndexOrThrow("action_delay")) shouldBe 500L
            cursor.getLong(cursor.getColumnIndexOrThrow("adjustment_delay")) shouldBe 200L
            cursor.getLong(cursor.getColumnIndexOrThrow("monitoring_duration")) shouldBe 30000L
            cursor.getFloat(cursor.getColumnIndexOrThrow("music_volume")) shouldBe 0.75f
            cursor.getFloat(cursor.getColumnIndexOrThrow("call_volume")) shouldBe 0.5f
            cursor.getFloat(cursor.getColumnIndexOrThrow("ring_volume")) shouldBe 0.3f
            cursor.getFloat(cursor.getColumnIndexOrThrow("notification_volume")) shouldBe 0.4f
            cursor.getFloat(cursor.getColumnIndexOrThrow("alarm_volume")) shouldBe 0.6f
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_lock")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_observing")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_rate_limiter")) shouldBe 1
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_increase_ms")) shouldBe 100L
            cursor.getLong(cursor.getColumnIndexOrThrow("volume_rate_limit_decrease_ms")) shouldBe 200L
            cursor.getInt(cursor.getColumnIndexOrThrow("volume_save_on_disconnect")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("keep_awake")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("nudge_volume")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("autoplay")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("launch_pkgs")) shouldBe """["com.spotify.music"]"""
            cursor.getInt(cursor.getColumnIndexOrThrow("show_home_screen")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("autoplay_keycodes")) shouldBe "[126,85]"
            cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("visible_adjustments")) shouldBe 0
            cursor.getString(cursor.getColumnIndexOrThrow("dnd_mode")) shouldBe "priority_only"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_type")) shouldBe "sound"
            cursor.getString(cursor.getColumnIndexOrThrow("connection_alert_sound_uri")) shouldBe
                    "content://media/external/audio/123"
        }
    }

    companion object {
        private const val DB_NAME = "migration-test-devices"
    }
}
