package eu.darken.bluemusic

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.MembersInjector
import eu.darken.bluemusic.bluetooth.core.BluetoothSourceFlow
import eu.darken.bluemusic.bluetooth.core.DeviceSourceModuleFlow
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.common.coroutines.CoroutineModule
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.common.dagger.ViewModelModule
import eu.darken.bluemusic.data.database.DatabaseModule
import eu.darken.bluemusic.data.device.DeviceDataModuleFlow
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.data.device.DeviceRepository
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.BlueMusicServiceFlow
import eu.darken.bluemusic.main.core.service.ServiceModule
import eu.darken.bluemusic.main.core.service.modules.EventModuleModule
import eu.darken.bluemusic.main.core.service.modules.VolumeModuleModule
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.ui.ViewModelBinder
import eu.darken.bluemusic.ui.about.AboutScreenHost
import eu.darken.bluemusic.ui.advanced.AdvancedScreenHost
import eu.darken.bluemusic.ui.config.ConfigScreenHost
import eu.darken.bluemusic.ui.discover.DiscoverScreenHost
import eu.darken.bluemusic.ui.intro.IntroScreenHost
import eu.darken.bluemusic.ui.manageddevices.ManagedDevicesScreenHost
import eu.darken.bluemusic.ui.settings.SettingsScreenHost
import eu.darken.bluemusic.util.EventGenerator

@AppComponent.Scope
@Component(modules = [
    AndroidModule::class,
    DeviceSourceModuleFlow::class,
    DeviceDataModuleFlow::class,
    ViewModelModule::class,
    ViewModelBinder::class,
    CoroutineModule::class,
    DatabaseModule::class,
    ServiceModule::class,
    EventModuleModule::class,
    VolumeModuleModule::class
])
interface AppComponent : MembersInjector<App> {
    fun inject(app: App)
    fun inject(service: BlueMusicServiceFlow)
    fun inject(mainActivity: eu.darken.bluemusic.main.ui.MainActivity)
    
    fun managedDevicesScreenHost(): ManagedDevicesScreenHost
    fun configScreenHost(): ConfigScreenHost
    fun discoverScreenHost(): DiscoverScreenHost
    fun introScreenHost(): IntroScreenHost
    fun settingsScreenHost(): SettingsScreenHost
    fun advancedScreenHost(): AdvancedScreenHost
    fun aboutScreenHost(): AboutScreenHost
    
    fun settings(): Settings
    fun deviceRepository(): DeviceRepository
    fun streamHelper(): StreamHelper
    fun fakeSpeakerDevice(): FakeSpeakerDevice
    fun deviceManagerFlow(): DeviceManagerFlow
    fun dispatcherProvider(): DispatcherProvider
    fun bluetoothSourceFlow(): BluetoothSourceFlow
    fun eventGenerator(): EventGenerator

    @Component.Builder interface Builder {
        @BindsInstance
        fun application(application: Application): Builder
        fun build(): AppComponent
    }

    @MustBeDocumented
    @javax.inject.Scope
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Scope
}