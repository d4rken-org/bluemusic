package eu.darken.bluemusic.data.device

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceConfigDao {
    
    @Query("SELECT * FROM device_configs ORDER BY last_connected DESC")
    fun getAllDevices(): Flow<List<DeviceConfigEntity>>
    
    @Query("SELECT * FROM device_configs WHERE address = :address")
    suspend fun getDevice(address: String): DeviceConfigEntity?
    
    @Query("SELECT * FROM device_configs WHERE address = :address")
    fun observeDevice(address: String): Flow<DeviceConfigEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceConfigEntity)
    
    @Update
    suspend fun updateDevice(device: DeviceConfigEntity)
    
    @Delete
    suspend fun deleteDevice(device: DeviceConfigEntity)
    
    @Query("DELETE FROM device_configs WHERE address = :address")
    suspend fun deleteByAddress(address: String)
    
    @Query("UPDATE device_configs SET last_connected = :timestamp WHERE address = :address")
    suspend fun updateLastConnected(address: String, timestamp: Long)
}