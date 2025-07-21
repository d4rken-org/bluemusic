package eu.darken.bluemusic.bluetooth.core.speaker

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeakerDeviceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun getSpeaker(
        isConnected: Boolean = true,
    ) = FakeSpeakerDevice(
        label = "${context.getString(R.string.label_device_speaker)} (${Build.MODEL}) ",
        isConnected = isConnected,
    )
}