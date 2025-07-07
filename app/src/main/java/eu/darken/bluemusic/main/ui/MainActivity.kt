package eu.darken.bluemusic.main.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import eu.darken.bluemusic.App
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme
import eu.darken.bluemusic.ui.navigation.MainNavigation
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.v("onCreate(savedInstanceState=%s)", savedInstanceState)
        (application as App).appComponent.inject(this)
        super.onCreate(savedInstanceState)

        setContent {
            BlueMusicTheme {
                MainNavigation()
            }
        }
    }
}