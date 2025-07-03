package eu.darken.bluemusic.data.device

import dagger.Binds
import dagger.Module

@Module
abstract class DeviceDataModuleFlow {
    
    @Binds
    abstract fun bindDeviceManager(adapter: DeviceManagerFlowAdapter): DeviceManagerFlow
}