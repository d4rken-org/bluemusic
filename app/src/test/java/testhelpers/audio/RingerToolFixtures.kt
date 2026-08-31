package testhelpers.audio

import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import io.mockk.every
import io.mockk.mockk

/** A [RingerTool] reporting [RingerMode.NORMAL], for tests that don't exercise ringer behaviour. */
fun normalRingerTool(): RingerTool = mockk(relaxed = true) {
    every { getCurrentRingerMode() } returns RingerMode.NORMAL
}
