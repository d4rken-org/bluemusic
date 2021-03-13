package eu.darken.bluemusic

import dagger.Component
import dagger.MembersInjector
import eu.darken.bluemusic.bluetooth.core.DeviceSourceModule

@AppComponent.Scope
@Component(modules = [
    ActivityBinderModule::class,
    ServiceBinderModule::class,
    ReceiverBinderModule::class,
    AndroidModule::class,
    DeviceSourceModule::class
])
interface AppComponent : MembersInjector<App> {
    fun inject(app: App)

    @Component.Builder interface Builder {
        fun androidModule(module: AndroidModule): Builder
        fun build(): AppComponent
    }

    @MustBeDocumented
    @Scope
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Scope
}