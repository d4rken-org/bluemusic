package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity

fun DeviceConfigEntity.toManagedDevice(): ManagedDevice {
    return ManagedDevice(
        address = address,
        name = name,
        alias = alias,
        lastConnected = lastConnected,
        actionDelay = actionDelay,
        adjustmentDelay = adjustmentDelay,
        monitoringDuration = monitoringDuration,
        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
        volumeLock = volumeLock,
        keepAwake = keepAwake,
        nudgeVolume = nudgeVolume,
        autoplay = autoplay,
        launchPkg = launchPkg
    )
}

fun ManagedDevice.toEntity(): DeviceConfigEntity {
    return DeviceConfigEntity(
        address = address,
        name = name,
        alias = alias,
        lastConnected = lastConnected,
        actionDelay = actionDelay,
        adjustmentDelay = adjustmentDelay,
        monitoringDuration = monitoringDuration,
        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
        volumeLock = volumeLock,
        keepAwake = keepAwake,
        nudgeVolume = nudgeVolume,
        autoplay = autoplay,
        launchPkg = launchPkg
    )
}