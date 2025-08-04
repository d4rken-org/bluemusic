package eu.darken.bluemusic.devices.core.database

interface DeviceConf {
    var address: String?
    var lastConnected: Long

    var actionDelay: Long?
    var adjustmentDelay: Long?
    var monitoringDuration: Long?

    var musicVolume: Float?
    var callVolume: Float?
    var ringVolume: Float?
    var notificationVolume: Float?
    var alarmVolume: Float?

    var volumeLock: Boolean

    var keepAwake: Boolean

    var nudgeVolume: Boolean

    var autoplay: Boolean

    var launchPkg: String?
}