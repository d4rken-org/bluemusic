package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import eu.darken.bluemusic.common.BuildWrap
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)

class VolumeToolTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var volumeTool: VolumeTool
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        fakeTime = 1000L
        audioManager = mockk(relaxed = true)
        audioLevels = AudioStream.Id.entries.associateWith { 0 }.toMutableMap()
        every { audioManager.getStreamMaxVolume(any()) } returns 15
        every { audioManager.getStreamVolume(any()) } answers {
            audioLevels[toStreamId(firstArg())] ?: 0
        }
        every { audioManager.setStreamVolume(any(), any(), any()) } answers {
            audioLevels[toStreamId(firstArg())] = secondArg()
        }

        volumeTool = VolumeTool(audioManager).apply {
            clock = { fakeTime }
        }
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap.VERSION)
    }

    private fun fakeSdk(level: Int) {
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.SDK_INT } returns level
        every { BuildWrap.VERSION.CODENAME } returns "REL"
    }

    @Test
    fun `changeVolume writes to AudioManager and marks observed level as self once`() = runTest {
        volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

        verify(exactly = 1) {
            audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, 10, 0)
        }
        volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe false
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
    }

    @Test
    fun `voice call write mirrors observer self classification to handsfree`() = runTest {
        volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 8)

        volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
    }

    @Test
    fun `already at target remembers recent target without pending observer write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_VOICE_CALL] = 10

        volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 10)

        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
        volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe false
        verify(exactly = 0) {
            audioManager.setStreamVolume(AudioStream.Id.STREAM_VOICE_CALL.id, 10, any())
        }
    }

    @Test
    fun `delayed stepped writes skip no-op start step and retain final recent target`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 4,
            delay = Duration.ofMillis(1),
        )

        // Old behavior wrote 2,3,4 (no-op start). New behavior writes only 3,4.
        writes shouldBe listOf(3, 4)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 4) shouldBe true
    }

    @Test
    fun `ramp from 9 to 20 writes exactly 11 levels with no trailing delay`() = runTest {
        every { audioManager.getStreamMaxVolume(any()) } returns 25
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 9
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        val started = currentTime
        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 20,
            delay = Duration.ofMillis(500),
        )
        val elapsed = currentTime - started

        writes shouldBe (10..20).toList()
        // Per setVolume(): each call adds 10ms before + 10ms after = 20ms.
        // 11 writes with 10 inter-write delays of 500ms (no trailing delay):
        // 11 * 20 (write overhead) + 10 * 500 (inter-write delays) = 220 + 5000 = 5220
        elapsed shouldBe 5220L
    }

    @Test
    fun `ramp downwards to target writes step-by-step skipping current level`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 10
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 7,
            delay = Duration.ofMillis(1),
        )

        // 10 → 7 should write 9, 8, 7 (skip 10)
        writes shouldBe listOf(9, 8, 7)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 7) shouldBe true
    }

    @Test
    fun `min greater than max returns false without writing or recording target`() = runTest {
        // Defensive guard: if the platform reports degenerate stream bounds (min > max),
        // VolumeTool aborts before calling coerceIn (which would throw IllegalArgumentException).
        every { audioManager.getStreamMaxVolume(any()) } returns -1
        // getMinVolume returns 0 in unit tests (Build.VERSION.SDK_INT==0 path), so 0 > -1.
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 0
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        val result = volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            delay = Duration.ofMillis(1),
        )

        result shouldBe false
        writes shouldBe emptyList()
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
    }

    @Test
    fun `target level below min is clamped to min`() = runTest {
        // In unit tests Build.VERSION.SDK_INT is 0 (no Robolectric), so getMinVolume returns 0.
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 3
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = -2,
            delay = Duration.ofMillis(1),
        )

        // Clamped to min=0. Should ramp down 3 → 2 → 1 → 0.
        writes shouldBe listOf(2, 1, 0)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 0) shouldBe true
    }

    @Test
    fun `ramp with visible=false uses flag 0 for every write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val flags = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            val flag = thirdArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            flags += flag
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            visible = false,
            delay = Duration.ofMillis(1),
        )

        flags shouldBe listOf(0, 0, 0)
    }

    @Test
    fun `ramp with visible=true uses FLAG_SHOW_UI for every write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val flags = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            val flag = thirdArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            flags += flag
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            visible = true,
            delay = Duration.ofMillis(1),
        )

        flags shouldBe listOf(
            android.media.AudioManager.FLAG_SHOW_UI,
            android.media.AudioManager.FLAG_SHOW_UI,
            android.media.AudioManager.FLAG_SHOW_UI,
        )
    }

    // In a pure-JVM test Build.VERSION.SDK_INT is 0, so queryActiveMediaRoute
    // takes the < API33 fallback branch (getDevices + a2dp/sco booleans).
    @Test
    fun `queryActiveMediaRoute falls back to available outputs below API33`() = runTest {
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
        every { audioManager.isBluetoothA2dpOn } returns false
        every { audioManager.isBluetoothScoOn } returns false

        val result = volumeTool.queryActiveMediaRoute().description

        result shouldContain "availableOnly=[none]"
        result shouldContain "a2dpOn=false"
        result shouldContain "no active-route API"
    }

    @Test
    fun `queryActiveMediaRoute swallows route-query failures`() = runTest {
        every { audioManager.getDevices(any()) } throws SecurityException("nope")

        volumeTool.queryActiveMediaRoute().description shouldBe "route-query-failed: SecurityException: nope"
    }

    // The API 33+ predicted branch can't run in plain JVM (AudioAttributes.Builder
    // is a stub), so the formatting is verified directly via the extracted helper.
    private fun audioDevice(type: Int, product: CharSequence?, addr: String = ""): AudioDeviceInfo = mockk {
        every { getType() } returns type
        every { productName } returns product
        every { getAddress() } returns addr
    }

    @Test
    fun `formatMediaRoute predicted lists type, raw id and product name`() {
        val speaker = audioDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone Speaker")

        volumeTool.formatMediaRoute(active = true, devices = listOf(speaker), a2dp = true, sco = false, queryMs = 0) shouldBe
            "predicted=[SPEAKER#${AudioDeviceInfo.TYPE_BUILTIN_SPEAKER} 'Phone Speaker'] a2dpOn=true scoOn=false queryMs=0"
    }

    @Test
    fun `formatMediaRoute labels bluetooth a2dp and omits blank product name`() {
        val bt = audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "   ")

        volumeTool.formatMediaRoute(active = true, devices = listOf(bt), a2dp = true, sco = false, queryMs = 3) shouldBe
            "predicted=[BT_A2DP#${AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}] a2dpOn=true scoOn=false queryMs=3"
    }

    @Test
    fun `formatMediaRoute maps unknown type to OTHER with raw id`() {
        val other = audioDevice(999, null)

        volumeTool.formatMediaRoute(active = true, devices = listOf(other), a2dp = false, sco = false, queryMs = 0) shouldContain "OTHER#999]"
    }

    @Test
    fun `formatMediaRoute reports none and availableOnly suffix`() {
        volumeTool.formatMediaRoute(active = false, devices = emptyList(), a2dp = false, sco = false, queryMs = 0) shouldBe
            "availableOnly=[none] a2dpOn=false scoOn=false queryMs=0 (no active-route API < API33)"
    }

    @Test
    fun `bluetoothRouteFrom is true for every bluetooth output type`() {
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
        ).forEach { type ->
            volumeTool.bluetoothRouteFrom(active = true, devices = listOf(audioDevice(type, null))) shouldBe true
        }
    }

    @Test
    fun `bluetoothRouteFrom is true when any routed device is bluetooth`() {
        val devices = listOf(
            audioDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null),
            audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null),
        )

        volumeTool.bluetoothRouteFrom(active = true, devices = devices) shouldBe true
    }

    @Test
    fun `bluetoothRouteFrom is false for speaker and wired outputs`() {
        volumeTool.bluetoothRouteFrom(
            active = true,
            devices = listOf(audioDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null)),
        ) shouldBe false

        volumeTool.bluetoothRouteFrom(
            active = true,
            devices = listOf(audioDevice(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, null)),
        ) shouldBe false
    }

    @Test
    fun `bluetoothRouteFrom is unknown for an empty device list`() {
        volumeTool.bluetoothRouteFrom(active = true, devices = emptyList()) shouldBe null
    }

    @Test
    fun `bluetoothRouteFrom is unknown below API33`() {
        volumeTool.bluetoothRouteFrom(
            active = false,
            devices = listOf(audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null)),
        ) shouldBe null
    }

    @Test
    fun `addressesFrom keeps non-blank addresses only`() {
        val devices = listOf(
            audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null, "AA:BB:CC:DD:EE:FF"),
            audioDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null, "   "),
            audioDevice(AudioDeviceInfo.TYPE_BLE_HEADSET, null, ""),
            audioDevice(AudioDeviceInfo.TYPE_BLE_SPEAKER, null, "11:22:33:44:55:66"),
        )

        volumeTool.addressesFrom(devices) shouldBe setOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66")
    }

    @Test
    fun `bluetoothAddressesFrom keeps bluetooth output addresses only`() {
        val devices = listOf(
            audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null, "AA:BB:CC:DD:EE:FF"),
            audioDevice(AudioDeviceInfo.TYPE_USB_HEADSET, null, "card=1;device=0"),
        )

        volumeTool.bluetoothAddressesFrom(devices) shouldBe setOf("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `bluetoothAddressesFrom is empty when the bluetooth output has no address`() {
        val devices = listOf(
            audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null, ""),
            audioDevice(AudioDeviceInfo.TYPE_USB_HEADSET, null, "card=1;device=0"),
        )

        volumeTool.bluetoothAddressesFrom(devices) shouldBe emptySet<String>()
    }

    @Test
    fun `resolveBoundedLevel without a band passes the target through`() {
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 1f, null) shouldBe 15
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 0.5f, null) shouldBe 8
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 0.5f, VolumeBand(null, null)) shouldBe 8
    }

    @Test
    fun `resolveBoundedLevel clamps in both directions`() {
        val band = VolumeBand(min = 0.2f, max = 0.5f)

        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 1f, band) shouldBe 7
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 0f, band) shouldBe 3
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 0.4f, band) shouldBe 6
    }

    @Test
    fun `bandLevels resolves an open bound to the stream bound`() {
        volumeTool.bandLevels(AudioStream.Id.STREAM_MUSIC, VolumeBand(min = null, max = 0.5f)) shouldBe 0..7
        volumeTool.bandLevels(AudioStream.Id.STREAM_MUSIC, VolumeBand(min = 0.2f, max = null)) shouldBe 3..15
    }

    @Test
    fun `bandLevels lets the maximum win over a conflicting minimum`() {
        val band = VolumeBand(min = 0.8f, max = 0.2f)

        volumeTool.bandLevels(AudioStream.Id.STREAM_MUSIC, band) shouldBe 3..3
        volumeTool.resolveBoundedLevel(AudioStream.Id.STREAM_MUSIC, 1f, band) shouldBe 3
    }

    /**
     * getMinVolume deliberately answers STREAM_BLUETOOTH_HANDSFREE with STREAM_VOICE_CALL's
     * minimum, the platform rejects the handsfree stream type itself. The limit slider's stops and
     * the enforcement in bandLevels both have to keep consuming that same minimum: if they drift
     * apart, a stop the user picks silently resolves to a different level.
     */
    @Test
    fun `handsfree limit slider stops match the levels bandLevels can enforce`() {
        fakeSdk(28)
        val stream = AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        every { audioManager.getStreamMinVolume(stream.id) } throws IllegalArgumentException("Bad stream type 6")
        every { audioManager.getStreamMinVolume(AudioStream.Id.STREAM_VOICE_CALL.id) } returns 1
        every { audioManager.getStreamMaxVolume(stream.id) } returns 15

        // DeviceConfigViewModel publishes `max - min` as the step count and the RangeSlider takes
        // it as `steps = stepCount - 1`, which leaves stepCount + 1 selectable stops.
        val stepCount = volumeTool.getMaxVolume(stream) - volumeTool.getMinVolume(stream)
        val sliderStops = stepCount + 1

        val enforceableLevels = (0..1000)
            .map { it / 1000f }
            .flatMap {
                listOf(
                    volumeTool.bandLevels(stream, VolumeBand(min = it, max = null)).first,
                    volumeTool.bandLevels(stream, VolumeBand(min = null, max = it)).last,
                )
            }
            .toSet()

        sliderStops shouldBe enforceableLevels.size
        // Pins the proxy itself: without it the minimum falls back to 0 and both sides would agree
        // on a grid the stream doesn't have.
        enforceableLevels shouldBe (1..15).toSet()
    }

    private fun toStreamId(id: Int): AudioStream.Id {
        return AudioStream.Id.entries.first { it.id == id }
    }
}
