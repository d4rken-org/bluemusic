package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.main.core.audio.AudioStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


//fun ManagedDevice.withUpdatedVolume(type: AudioStream.Type, volume: Float?): DeviceConfigEntity = when (type) {
//    AudioStream.Type.MUSIC -> config.copy(musicVolume = volume)
//    AudioStream.Type.CALL -> config.copy(callVolume = volume)
//    AudioStream.Type.RINGTONE -> config.copy(ringVolume = volume)
//    AudioStream.Type.NOTIFICATION -> config.copy(notificationVolume = volume)
//    AudioStream.Type.ALARM -> config.copy(alarmVolume = volume)
//}

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