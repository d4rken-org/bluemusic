package eu.darken.bluemusic.devices.core.database

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migration 1 → 2: Add new columns and copy data from old column
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add the two new columns for separate increase/decrease rate limits
        db.execSQL("ALTER TABLE device_configs ADD COLUMN volume_rate_limit_increase_ms INTEGER")
        db.execSQL("ALTER TABLE device_configs ADD COLUMN volume_rate_limit_decrease_ms INTEGER")

        // Copy existing rate limit values to both new columns
        db.execSQL("""
            UPDATE device_configs
            SET volume_rate_limit_increase_ms = volume_rate_limit_ms,
                volume_rate_limit_decrease_ms = volume_rate_limit_ms
            WHERE volume_rate_limit_ms IS NOT NULL
        """.trimIndent())
    }
}

// Migration 2 → 3: Delete old column using AutoMigration
@DeleteColumn(tableName = "device_configs", columnName = "volume_rate_limit_ms")
class Migration2To3 : AutoMigrationSpec
