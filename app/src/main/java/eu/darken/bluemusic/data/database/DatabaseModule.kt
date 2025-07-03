package eu.darken.bluemusic.data.database

import android.content.Context
import dagger.Module
import dagger.Provides
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.data.device.DeviceConfigDao

@Module
class DatabaseModule {
    
    @Provides
    @AppComponent.Scope
    fun provideDatabase(context: Context): BlueMusicDatabase {
        return BlueMusicDatabase.getInstance(context)
    }
    
    @Provides
    @AppComponent.Scope
    fun provideDeviceConfigDao(database: BlueMusicDatabase): DeviceConfigDao {
        return database.deviceConfigDao()
    }
}