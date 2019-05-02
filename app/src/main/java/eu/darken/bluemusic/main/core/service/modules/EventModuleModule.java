package eu.darken.bluemusic.main.core.service.modules;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.main.core.service.modules.events.AppLaunchModule;
import eu.darken.bluemusic.main.core.service.modules.events.AutoplayModule;
import eu.darken.bluemusic.main.core.service.modules.events.CallMonitorModule;
import eu.darken.bluemusic.main.core.service.modules.events.CallVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.events.FakeDeviceConnectModule;
import eu.darken.bluemusic.main.core.service.modules.events.MusicMonitorModule;
import eu.darken.bluemusic.main.core.service.modules.events.MusicVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.events.NotificationMonitorModule;
import eu.darken.bluemusic.main.core.service.modules.events.NotificationVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.events.RingMonitorModule;
import eu.darken.bluemusic.main.core.service.modules.events.RingVolumeModule;

@Module
public abstract class EventModuleModule {

    @Binds
    @IntoMap
    @EventModuleKey(MusicVolumeModule.class)
    abstract EventModule musicVolume(MusicVolumeModule module);

    @Binds
    @IntoMap
    @EventModuleKey(MusicMonitorModule.class)
    abstract EventModule musicMonitor(MusicMonitorModule module);

    @Binds
    @IntoMap
    @EventModuleKey(CallVolumeModule.class)
    abstract EventModule callVolume(CallVolumeModule module);

    @Binds
    @IntoMap
    @EventModuleKey(CallMonitorModule.class)
    abstract EventModule callMonitor(CallMonitorModule module);

    @Binds
    @IntoMap
    @EventModuleKey(RingVolumeModule.class)
    abstract EventModule ringVolume(RingVolumeModule module);
    @Binds
    @IntoMap
    @EventModuleKey(RingMonitorModule.class)
    abstract EventModule ringMonitor(RingMonitorModule module);

    @Binds
    @IntoMap
    @EventModuleKey(NotificationVolumeModule.class)
    abstract EventModule notificationVolume(NotificationVolumeModule module);

    @Binds
    @IntoMap
    @EventModuleKey(NotificationMonitorModule.class)
    abstract EventModule notificationMonitor(NotificationMonitorModule module);

    @Binds
    @IntoMap
    @EventModuleKey(AutoplayModule.class)
    abstract EventModule autoplay(AutoplayModule module);

    @Binds
    @IntoMap
    @EventModuleKey(FakeDeviceConnectModule.class)
    abstract EventModule fakeDevice(FakeDeviceConnectModule module);

    @Binds
    @IntoMap
    @EventModuleKey(AppLaunchModule.class)
    abstract EventModule appLaunch(AppLaunchModule module);
}
