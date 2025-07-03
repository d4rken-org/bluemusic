package eu.darken.bluemusic.common

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import eu.darken.bluemusic.App
import eu.darken.bluemusic.AppComponent

/**
 * Temporary service locator until we can properly migrate to Hilt or manual DI
 */
object ServiceLocator {
    
    fun getAppComponent(context: Context): AppComponent {
        return (context.applicationContext as App).appComponent
    }
    
    fun inject(service: Service) {
        // Services will need to manually retrieve their dependencies
        // This is a temporary solution
    }
    
    fun inject(receiver: BroadcastReceiver) {
        // Receivers will need to manually retrieve their dependencies
        // This is a temporary solution
    }
}