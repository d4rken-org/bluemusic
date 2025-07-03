package eu.darken.bluemusic.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import eu.darken.bluemusic.data.device.DeviceConfigDao
import eu.darken.bluemusic.data.device.DeviceConfigEntity

@Database(
    entities = [DeviceConfigEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BlueMusicDatabase : RoomDatabase() {
    
    abstract fun deviceConfigDao(): DeviceConfigDao
    
    companion object {
        private const val DATABASE_NAME = "bluemusic_database.db"
        
        @Volatile
        private var INSTANCE: BlueMusicDatabase? = null
        
        fun getInstance(context: Context): BlueMusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): BlueMusicDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BlueMusicDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}