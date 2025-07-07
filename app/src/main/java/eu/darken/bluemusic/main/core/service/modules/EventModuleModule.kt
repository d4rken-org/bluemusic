package eu.darken.bluemusic.main.core.service.modules

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.darken.bluemusic.main.core.service.modules.events.AlarmMonitorModule
import eu.darken.bluemusic.main.core.service.modules.events.AlarmVolumeModule
import eu.darken.bluemusic.main.core.service.modules.events.AppLaunchModule
import eu.darken.bluemusic.main.core.service.modules.events.AutoplayModule
import eu.darken.bluemusic.main.core.service.modules.events.CallMonitorModule
import eu.darken.bluemusic.main.core.service.modules.events.CallVolumeModule
import eu.darken.bluemusic.main.core.service.modules.events.FakeDeviceConnectModule
import eu.darken.bluemusic.main.core.service.modules.events.KeepAwakeModule
import eu.darken.bluemusic.main.core.service.modules.events.MusicMonitorModule
import eu.darken.bluemusic.main.core.service.modules.events.MusicVolumeModule
import eu.darken.bluemusic.main.core.service.modules.events.NotificationMonitorModule
import eu.darken.bluemusic.main.core.service.modules.events.NotificationVolumeModule
import eu.darken.bluemusic.main.core.service.modules.events.RingMonitorModule
import eu.darken.bluemusic.main.core.service.modules.events.RingVolumeModule

//
//@Module
//abstract class EventModuleModule {
//    @Binds @IntoMap @EventModuleKey(MusicVolumeModule::class)
//    abstract fun musicVolume(module: MusicVolumeModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(MusicMonitorModule::class)
//    internal abstract fun musicMonitor(module: MusicMonitorModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(CallVolumeModule::class)
//    abstract fun callVolume(module: CallVolumeModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(CallMonitorModule::class)
//    internal abstract fun callMonitor(module: CallMonitorModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(RingVolumeModule::class)
//    abstract fun ringVolume(module: RingVolumeModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(RingMonitorModule::class)
//    internal abstract fun ringMonitor(module: RingMonitorModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(NotificationVolumeModule::class)
//    abstract fun notificationVolume(module: NotificationVolumeModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(NotificationMonitorModule::class)
//    internal abstract fun notificationMonitor(module: NotificationMonitorModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(AlarmVolumeModule::class)
//    abstract fun alarmVolume(module: AlarmVolumeModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(AlarmMonitorModule::class)
//    internal abstract fun alarmMonitor(module: AlarmMonitorModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(AutoplayModule::class) abstract fun autoplay(module: AutoplayModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(FakeDeviceConnectModule::class)
//    abstract fun fakeDevice(module: FakeDeviceConnectModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(AppLaunchModule::class)
//    abstract fun appLaunch(module: AppLaunchModule?): EventModule?
//
//    @Binds @IntoMap @EventModuleKey(KeepAwakeModule::class)
//    abstract fun keepAwape(module: KeepAwakeModule?): EventModule?
//}
