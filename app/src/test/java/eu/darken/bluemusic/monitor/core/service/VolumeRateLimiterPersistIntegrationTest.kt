package eu.darken.bluemusic.monitor.core.service

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.audio.levelToPercentage
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeRateLimiterModule
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeUpdateModule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.audio.normalRingerTool
import testhelpers.time.FakeMonotonicClock

/**
 * Regression coverage for the rate-limiter/observe-changes interplay:
 * the limiter (priority 5) physically corrects a volume jump before
 * VolumeUpdateModule (priority 10) runs, but the dispatched event still
 * carries the original high value. VolumeUpdateModule must persist the
 * live hardware level, not the event's value — otherwise a blocked jump
 * (e.g. Zello Auto-Volume) gets stored and restored on the next connect.
 */
class VolumeRateLimiterPersistIntegrationTest : BaseTest() {

    @Test
    fun `rate-limited jump persists the clamped level, not the event's value`() = runTest {
        // stored maps to level 5, hardware at 5, no prior limiter state
        val fixture = Fixture.create(this, initialMusicVolume = levelToPercentage(5, 0, 15))

        // Zello-style jump: an external app sets hardware 5 → 15
        fixture.setHardware(15)
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false))

        // Limiter clamped hardware to one step above the reference
        fixture.hardware() shouldBe 6
        // Persisted value matches the clamp, not the original jump
        fixture.storedMusicVolume() shouldBe levelToPercentage(6, 0, 15)
        fixture.totalWriteCount() shouldBe 1

        // The limiter's corrective write surfaces as a self event → no further persist
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 15, newVolume = 6, self = true))
        fixture.storedMusicVolume() shouldBe levelToPercentage(6, 0, 15)
        fixture.totalWriteCount() shouldBe 1
    }

    @Test
    fun `jump within rate window is reverted and never persisted`() = runTest {
        val fixture = Fixture.create(this, initialMusicVolume = levelToPercentage(6, 0, 15))

        // Establish limiter reference state with an allowed single step 5 → 6
        fixture.setHardware(6)
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false))
        fixture.totalWriteCount() shouldBe 0 // stored already maps to level 6

        // Jump within the rate window (clock unchanged)
        fixture.setHardware(15)
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 6, newVolume = 15, self = false))

        fixture.hardware() shouldBe 6
        fixture.storedMusicVolume() shouldBe levelToPercentage(6, 0, 15)
        fixture.totalWriteCount() shouldBe 0
    }

    @Test
    fun `handsfree call stream jump is clamped and persisted at the clamped level`() = runTest {
        val fixture = Fixture.create(
            this,
            initialMusicVolume = levelToPercentage(5, 0, 15),
            initialCallVolume = levelToPercentage(5, 0, 15),
        )

        fixture.setHardware(15, AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE)
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, oldVolume = 5, newVolume = 15, self = false))

        fixture.hardware(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe 6
        fixture.storedCallVolume() shouldBe levelToPercentage(6, 0, 15)
        fixture.totalWriteCount() shouldBe 1

        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, oldVolume = 15, newVolume = 6, self = true))
        fixture.totalWriteCount() shouldBe 1
    }

    @Test
    fun `grouped earbuds - one hardware correction, both configs persist the clamped level`() = runTest {
        // Order matters for the regression: the per-device fold would let the
        // 0ms member (processed second) step past the sibling's clamp to 7.
        val fixture = Fixture.createGroup(
            this,
            listOf(
                Fixture.DeviceSpec(
                    address = "AA:BB:CC:DD:EE:01",
                    label = "Buds3 Pro",
                    musicVolume = levelToPercentage(5, 0, 15),
                    rateLimitIncreaseMs = 1000L,
                ),
                Fixture.DeviceSpec(
                    address = "AA:BB:CC:DD:EE:02",
                    label = "Buds3 Pro",
                    musicVolume = levelToPercentage(5, 0, 15),
                    rateLimitIncreaseMs = 0L,
                ),
            ),
        )

        fixture.setHardware(15)
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false))

        fixture.hardware() shouldBe 6
        fixture.hardwareWriteCount(AudioStream.Id.STREAM_MUSIC) shouldBe 1
        fixture.storedMusicVolume("AA:BB:CC:DD:EE:01") shouldBe levelToPercentage(6, 0, 15)
        fixture.storedMusicVolume("AA:BB:CC:DD:EE:02") shouldBe levelToPercentage(6, 0, 15)
        fixture.totalWriteCount() shouldBe 2

        // Self follow-up from the limiter's correction adds neither persists nor hardware writes
        fixture.dispatch(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 15, newVolume = 6, self = true))
        fixture.totalWriteCount() shouldBe 2
        fixture.hardwareWriteCount(AudioStream.Id.STREAM_MUSIC) shouldBe 1
    }

    private class Fixture(
        scope: TestScope,
        private val specs: List<DeviceSpec>,
    ) {
        data class DeviceSpec(
            val address: String,
            val label: String = "Test Device",
            val musicVolume: Float? = null,
            val callVolume: Float? = null,
            val rateLimitIncreaseMs: Long? = null,
        )

        private val audioLevels = mutableMapOf(
            AudioStream.Id.STREAM_MUSIC to 5,
            AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE to 5,
        )
        private val writeLog = mutableListOf<Pair<DeviceConfigEntity, DeviceConfigEntity>>()
        private val hardwareWrites = mutableListOf<Pair<AudioStream.Id, Int>>()

        private val audioManager = mockk<AudioManager>(relaxed = true)
        private val ringerTool = mockk<RingerTool>()
        private val deviceRepo = mockk<DeviceRepo>()
        private val clock = FakeMonotonicClock(now = 10_000L)
        private val observationGate = VolumeObservationGate()
        private val ownerRegistry = eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry()

        private fun sourceDevice(spec: DeviceSpec) = mockk<SourceDevice> {
            every { this@mockk.address } returns spec.address
            every { label } returns spec.label
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            // Headset-realistic: CALL maps to the handsfree stream
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }

        private val devicesFlow = MutableStateFlow(
            specs.map { spec ->
                ManagedDevice(
                    isConnected = true,
                    device = sourceDevice(spec),
                    config = DeviceConfigEntity(
                        address = spec.address,
                        musicVolume = spec.musicVolume,
                        callVolume = spec.callVolume,
                        volumeObserving = true,
                        volumeRateLimiter = true,
                        volumeRateLimitIncreaseMs = spec.rateLimitIncreaseMs,
                        isEnabled = true,
                        lastConnected = 0L, // long past → device counts as stable
                    ),
                )
            }
        )

        private val volumeTool = VolumeTool(audioManager).apply {
            clock = { scope.testScheduler.currentTime }
        }

        private val dispatcher = VolumeEventDispatcher(
            setOf(
                VolumeRateLimiterModule(
                    volumeTool = volumeTool,
                    limitEnforcer = VolumeLimitEnforcer(volumeTool, normalRingerTool()),
                    deviceRepo = deviceRepo,
                    ownerRegistry = ownerRegistry,
                    clock = clock,
                ),
                VolumeUpdateModule(
                    volumeTool = volumeTool,
                    limitEnforcer = VolumeLimitEnforcer(volumeTool, normalRingerTool()),
                    ringerTool = ringerTool,
                    deviceRepo = deviceRepo,
                    observationGate = observationGate,
                    ownerRegistry = ownerRegistry,
                ),
            )
        )

        init {
            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
            every { deviceRepo.devices } returns devicesFlow

            every { audioManager.getStreamMaxVolume(any()) } returns 15
            every { audioManager.getStreamVolume(any()) } answers {
                audioLevels[toStreamId(firstArg())] ?: 0
            }
            every { audioManager.setStreamVolume(any(), any(), any()) } answers {
                val stream = toStreamId(firstArg())
                audioLevels[stream] = secondArg()
                hardwareWrites += stream to secondArg()
            }

            coEvery { deviceRepo.updateDevice(any(), any()) } coAnswers {
                val addr = firstArg<String>()
                val transform = secondArg<(DeviceConfigEntity) -> DeviceConfigEntity>()
                val devices = devicesFlow.value.toMutableList()
                val index = devices.indexOfFirst { it.address == addr }
                check(index >= 0) { "Unknown device update: $addr" }

                val current = devices[index]
                val updatedConfig = transform(current.config)
                writeLog += current.config to updatedConfig
                devices[index] = current.copy(config = updatedConfig)
                devicesFlow.value = devices
            }
        }

        private suspend fun registerOwner() {
            specs.forEachIndexed { index, spec ->
                ownerRegistry.onDeviceConnected(
                    address = spec.address,
                    label = spec.label,
                    deviceType = SourceDevice.Type.HEADPHONES,
                    receivedAtElapsedMs = 1000L + index * 2,
                    sequence = index.toLong(),
                )
            }
        }

        suspend fun dispatch(event: VolumeEvent) = dispatcher.dispatch(event)

        fun setHardware(level: Int, stream: AudioStream.Id = AudioStream.Id.STREAM_MUSIC) {
            audioLevels[stream] = level
        }

        fun hardware(stream: AudioStream.Id = AudioStream.Id.STREAM_MUSIC): Int = audioLevels.getValue(stream)

        fun storedMusicVolume(address: String = specs.first().address): Float? =
            devicesFlow.value.first { it.address == address }.config.musicVolume

        fun storedCallVolume(address: String = specs.first().address): Float? =
            devicesFlow.value.first { it.address == address }.config.callVolume

        /** Every updateDevice call, including no-op writes — pins "no second persist attempt". */
        fun totalWriteCount(): Int = writeLog.size

        /** Every setStreamVolume call for the stream — pins "exactly one hardware correction". */
        fun hardwareWriteCount(stream: AudioStream.Id): Int = hardwareWrites.count { it.first == stream }

        private fun toStreamId(rawStreamId: Int): AudioStream.Id =
            AudioStream.Id.entries.first { it.id == rawStreamId }

        companion object {
            suspend fun create(
                scope: TestScope,
                initialMusicVolume: Float,
                initialCallVolume: Float? = null,
            ): Fixture = createGroup(
                scope,
                listOf(DeviceSpec(address = "AA:BB:CC:DD:EE:FF", musicVolume = initialMusicVolume, callVolume = initialCallVolume)),
            )

            suspend fun createGroup(scope: TestScope, specs: List<DeviceSpec>): Fixture =
                Fixture(scope, specs).apply { registerOwner() }
        }
    }
}
