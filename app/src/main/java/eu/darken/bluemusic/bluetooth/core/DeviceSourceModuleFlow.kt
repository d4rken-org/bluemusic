package eu.darken.bluemusic.bluetooth.core

import dagger.Binds
import dagger.Module

@Module
abstract class DeviceSourceModuleFlow {
    
    @Binds
    abstract fun bindBluetoothSource(source: LiveBluetoothSourceFlow): BluetoothSourceFlow
}