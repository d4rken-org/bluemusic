package eu.darken.bluemusic.main.core.service;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.main.core.service.modules.AppLaunchModule;
import eu.darken.bluemusic.main.core.service.modules.AutoplayModule;
import eu.darken.bluemusic.main.core.service.modules.CallVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.FakeDeviceConnectModule;
import eu.darken.bluemusic.main.core.service.modules.MusicVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.NotificationVolumeModule;
import eu.darken.bluemusic.main.core.service.modules.RingVolumeModule;

@Module
public abstract class ActionModuleModule {

    @Binds
    @IntoMap
    @ActionModuleKey(MusicVolumeModule.class)
    abstract ActionModule musicVolume(MusicVolumeModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(CallVolumeModule.class)
    abstract ActionModule callVolume(CallVolumeModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(RingVolumeModule.class)
    abstract ActionModule ringVolume(RingVolumeModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(NotificationVolumeModule.class)
    abstract ActionModule notificationVolume(NotificationVolumeModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(AutoplayModule.class)
    abstract ActionModule autoplay(AutoplayModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(FakeDeviceConnectModule.class)
    abstract ActionModule fakeDevice(FakeDeviceConnectModule module);

    @Binds
    @IntoMap
    @ActionModuleKey(AppLaunchModule.class)
    abstract ActionModule appLaunch(AppLaunchModule module);
}
