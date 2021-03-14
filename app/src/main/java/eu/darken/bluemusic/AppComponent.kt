package eu.darken.bluemusic

import android.app.Application
import dagger.BindsInstance
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
        @BindsInstance
        fun application(application: Application): Builder
        fun build(): AppComponent
    }

    @MustBeDocumented
    @javax.inject.Scope
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Scope
}