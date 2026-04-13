package eu.darken.bluemusic.monitor.core.service

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.connection.CallVolumeModule
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeUpdateModule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.asDispatcherProvider
import testhelpers.time.FakeMonotonicClock

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherVolumeIntegrationTest : BaseTest() {

    @Test
    fun `mirrored handsfree observation is ignored while fake speaker call dispatch is active`() = runTest {
        val fixture = Fixture(this)
        val job = fixture.launchConnectDispatch()

        fixture.advanceToMonitorWindow()

        fixture.observationGate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe true
        fixture.speakerLastConnectedWasUpdated() shouldBe true

        fixture.injectHandsfreeRestore(level = 11)
        fixture.volumeUpdateModule.handle(
            VolumeEvent(
                streamId = AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE,
                oldVolume = 1,
                newVolume = 11,
                self = false,
            )
        )

        fixture.headsetCallVolume() shouldBe fixture.initialHeadsetCallVolume
        fixture.headsetCallWriteCount() shouldBe 0

        fixture.finishDispatch(job)
    }

    @Test
    fun `mirrored handsfree observation persists after fake speaker call dispatch finishes`() = runTest {
        val fixture = Fixture(this)
        val job = fixture.launchConnectDispatch()

        fixture.advanceToMonitorWindow()
        fixture.finishDispatch(job)

        fixture.observationGate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe false
        fixture.speakerLastConnectedWasUpdated() shouldBe true

        fixture.injectHandsfreeRestore(level = 11)
        fixture.volumeUpdateModule.handle(
            VolumeEvent(
                streamId = AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE,
                oldVolume = 1,
                newVolume = 11,
                self = false,
            )
        )

        fixture.headsetCallVolume() shouldBe (11f / 15f)
        fixture.headsetCallWriteCount() shouldBe 1
    }

    private class Fixture(
        private val scope: TestScope,
    ) {
        val initialHeadsetCallVolume = 0.4f

        private val headsetAddress = "34:E3:FB:94:C2:AF"
        private val speakerAddress = FakeSpeakerDevice.ADDRESS
        private val actionDelayMs = 1_000L
        private val monitoringDurationMs = 1_000L

        private val audioLevels = mutableMapOf(
            AudioStream.Id.STREAM_MUSIC to 5,
            AudioStream.Id.STREAM_ALARM to 5,
            AudioStream.Id.STREAM_NOTIFICATION to 5,
            AudioStream.Id.STREAM_RINGTONE to 5,
            AudioStream.Id.STREAM_VOICE_CALL to 11,
            AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE to 11,
        )

        private val writeLog = mutableListOf<DeviceWrite>()
        private val volumeEvents = MutableSharedFlow<VolumeEvent>()
        private val enabledState = DevicesSettings.EnabledState(
            isEnabled = true,
            toggleEpoch = 0L,
        )
        private val enabledStateFlow = MutableStateFlow(enabledState)

        private val audioManager = mockk<AudioManager>(relaxed = true)
        private val volumeObserver = mockk<VolumeObserver>()
        private val ringerTool = mockk<RingerTool>()
        private val deviceRepo = mockk<DeviceRepo>(relaxed = true)
        private val devicesSettings = mockk<DevicesSettings>()

        private val stableHeadset = SourceDeviceWrapper(
            address = headsetAddress,
            alias = "AirPods",
            name = "AirPods Pro",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        )
        private val fakeSpeaker = FakeSpeakerDevice(
            label = "Phone speaker",
            isConnected = true,
        )

        private val devicesFlow = MutableStateFlow(
            listOf(
                ManagedDevice(
                    isConnected = true,
                    device = stableHeadset,
                    config = DeviceConfigEntity(
                        address = headsetAddress,
                        lastConnected = 0L,
                        callVolume = initialHeadsetCallVolume,
                        volumeObserving = true,
                        isEnabled = true,
                    ),
                ),
                ManagedDevice(
                    isConnected = true,
                    device = fakeSpeaker,
                    config = DeviceConfigEntity(
                        address = speakerAddress,
                        lastConnected = 0L,
                        callVolume = 1f / 15f,
                        actionDelay = actionDelayMs,
                        adjustmentDelay = 0L,
                        monitoringDuration = monitoringDurationMs,
                        isEnabled = true,
                    ),
                ),
            )
        )

        val observationGate = VolumeObservationGate()

        private val volumeTool = VolumeTool(audioManager).apply {
            clock = { scope.testScheduler.currentTime }
        }
        val volumeUpdateModule = VolumeUpdateModule(
            volumeTool = volumeTool,
            ringerTool = ringerTool,
            deviceRepo = deviceRepo,
            observationGate = observationGate,
        )
        private val callVolumeModule = CallVolumeModule(
            volumeTool = volumeTool,
            volumeObserver = volumeObserver,
            observationGate = observationGate,
        )
        private val tracker by lazy {
            EventTypeDedupTracker(
                appScope = scope.backgroundScope,
                devicesSettings = devicesSettings,
                clock = FakeMonotonicClock(),
            )
        }
        private val dispatcher by lazy {
            EventDispatcher(
                dispatcherProvider = scope.coroutineContext.asDispatcherProvider(),
                deviceRepo = deviceRepo,
                devicesSettings = devicesSettings,
                connectionModuleMap = setOf(callVolumeModule),
                eventTypeDedupTracker = tracker,
            )
        }

        init {
            every { volumeObserver.volumes } returns volumeEvents
            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
            every { deviceRepo.devices } returns devicesFlow
            every { devicesSettings.enabledState } returns enabledStateFlow
            coEvery { devicesSettings.currentEnabledState() } returns enabledState

            every { audioManager.getStreamMaxVolume(any()) } returns 15
            every { audioManager.getStreamVolume(any()) } answers {
                audioLevels[toStreamId(firstArg())] ?: 0
            }
            every { audioManager.setStreamVolume(any(), any(), any()) } answers {
                val streamId = toStreamId(firstArg())
                val level = secondArg<Int>()
                audioLevels[streamId] = level
                if (streamId == AudioStream.Id.STREAM_VOICE_CALL) {
                    audioLevels[AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE] = level
                }
            }

            coEvery { deviceRepo.updateDevice(any(), any()) } coAnswers {
                val address = firstArg<String>()
                val transform = secondArg<(DeviceConfigEntity) -> DeviceConfigEntity>()
                val devices = devicesFlow.value.toMutableList()
                val index = devices.indexOfFirst { it.address == address }
                check(index >= 0) { "Unknown device update: $address" }

                val current = devices[index]
                val updatedConfig = transform(current.config)
                writeLog += DeviceWrite(
                    address = address,
                    before = current.config,
                    after = updatedConfig,
                )
                devices[index] = current.copy(config = updatedConfig)
                devicesFlow.value = devices
            }

        }

        fun launchConnectDispatch(): Job = scope.launch {
            dispatcher.dispatch(
                BluetoothEventQueue.Event(
                    type = BluetoothEventQueue.Event.Type.CONNECTED,
                    sourceDevice = fakeSpeaker,
                )
            )
        }

        fun advanceToMonitorWindow() {
            scope.runCurrent()
            scope.advanceTimeBy(actionDelayMs + 25L)
            scope.runCurrent()

            observationGate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe true
            audioLevels[AudioStream.Id.STREAM_VOICE_CALL] shouldBe 1
            audioLevels[AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE] shouldBe 1
        }

        fun finishDispatch(job: Job) {
            scope.advanceUntilIdle()
            scope.runCurrent()
            job.isCompleted shouldBe true
        }

        fun injectHandsfreeRestore(level: Int) {
            audioLevels[AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE] = level
        }

        fun headsetCallVolume(): Float? = currentConfig(headsetAddress).callVolume

        fun headsetCallWriteCount(): Int = writeLog.count { write ->
            write.address == headsetAddress && write.before.callVolume != write.after.callVolume
        }

        fun speakerLastConnectedWasUpdated(): Boolean {
            val config = currentConfig(speakerAddress)
            return config.lastConnected > 0L
        }

        private fun currentConfig(address: String): DeviceConfigEntity =
            devicesFlow.value.first { it.address == address }.config

        private fun toStreamId(rawStreamId: Int): AudioStream.Id =
            AudioStream.Id.entries.first { it.id == rawStreamId }
    }

    private data class DeviceWrite(
        val address: String,
        val before: DeviceConfigEntity,
        val after: DeviceConfigEntity,
    )
}
