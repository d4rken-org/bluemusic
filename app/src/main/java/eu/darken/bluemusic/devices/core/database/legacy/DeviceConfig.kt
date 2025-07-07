package eu.darken.bluemusic.devices.core.database.legacy

import eu.darken.bluemusic.devices.core.database.DeviceConf
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

@RealmClass
open class DeviceConfig : RealmObject(), DeviceConf {

    @PrimaryKey
    override var address: String? = null
    override var lastConnected: Long = 0

    override var actionDelay: Long? = null
    override var adjustmentDelay: Long? = null
    override var monitoringDuration: Long? = null

    override var musicVolume: Float? = null
    override var callVolume: Float? = null
    override var ringVolume: Float? = null
    override var notificationVolume: Float? = null
    override var alarmVolume: Float? = null

    override var volumeLock: Boolean = false

    override var keepAwake: Boolean = false

    override var nudgeVolume: Boolean = false

    override var autoplay: Boolean = false

    override var launchPkg: String? = null
}