package eu.darken.bluemusic.data.device

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