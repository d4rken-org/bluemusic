package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.toFloat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

fun DeviceRepo.observeDevice(address: String): Flow<ManagedDevice?> = devices.map { devs -> devs.firstOrNull { it.address == address } }

suspend fun DeviceRepo.getDevice(address: String): ManagedDevice? = observeDevice(address).first()

suspend fun DeviceRepo.currentDevices(): Collection<ManagedDevice> = devices.first()

fun DeviceConfigEntity.updateVolume(type: AudioStream.Type, volume: Float?): DeviceConfigEntity = when (type) {
    AudioStream.Type.MUSIC -> copy(musicVolume = volume)
    AudioStream.Type.CALL -> copy(callVolume = volume)
    AudioStream.Type.RINGTONE -> copy(ringVolume = volume)
    AudioStream.Type.NOTIFICATION -> copy(notificationVolume = volume)
    AudioStream.Type.ALARM -> copy(alarmVolume = volume)
}

/**
 * Type-safe volume update using VolumeMode
 */
fun DeviceConfigEntity.updateVolume(type: AudioStream.Type, mode: VolumeMode?): DeviceConfigEntity =
    updateVolume(type, mode.toFloat())

/**
 * Get volume as VolumeMode for type-safe handling
 */
fun DeviceConfigEntity.getVolumeMode(type: AudioStream.Type): VolumeMode? = when (type) {
    AudioStream.Type.MUSIC -> fromFloat(musicVolume)
    AudioStream.Type.CALL -> fromFloat(callVolume)
    AudioStream.Type.RINGTONE -> fromFloat(ringVolume)
    AudioStream.Type.NOTIFICATION -> fromFloat(notificationVolume)
    AudioStream.Type.ALARM -> fromFloat(alarmVolume)
}