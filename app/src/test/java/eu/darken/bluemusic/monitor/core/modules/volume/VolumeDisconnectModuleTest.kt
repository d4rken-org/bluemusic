package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeDisconnectModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var ringerTool: RingerTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var sourceDevice: SourceDevice

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        ringerTool = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        coEvery { deviceRepo.updateDevice(any(), any()) } just Runs

        sourceDevice = mockk {
            every { this@mockk.address } returns this@VolumeDisconnectModuleTest.address
            every { label } returns "Test Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }
    }

    private fun createModule() = VolumeDisconnectModule(
        volumeTool = volumeTool,
        ringerTool = ringerTool,
        deviceRepo = deviceRepo,
    )

    private fun config(
        musicVolume: Float? = null,
        callVolume: Float? = null,
        ringVolume: Float? = null,
        notificationVolume: Float? = null,
        alarmVolume: Float? = null,
        volumeSaveOnDisconnect: Boolean = true,
    ): DeviceConfigEntity = DeviceConfigEntity(
        address = address,
        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
        volumeSaveOnDisconnect = volumeSaveOnDisconnect,
    )

    private fun managedDevice(config: DeviceConfigEntity) = ManagedDevice(
        isConnected = true,
        device = sourceDevice,
        config = config,
    )

    private fun mockStream(id: AudioStream.Id, current: Int, max: Int) {
        every { volumeTool.getCurrentVolume(id) } returns current
        every { volumeTool.getMaxVolume(id) } returns max
    }

    /**
     * Invokes the transform lambda passed to [DeviceRepo.updateDevice] with the
     * given `seedConfig` and returns the [DeviceConfigEntity] it produced. Use to
     * assert what the module would write for the captured transform, free of
     * dependence on `event.device.config`.
     */
    private suspend fun runTransform(
        module: VolumeDisconnectModule,
        event: DeviceEvent,
        seedConfig: DeviceConfigEntity,
    ): DeviceConfigEntity {
        val slot = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { deviceRepo.updateDevice(address, capture(slot)) } just Runs
        module.handle(event)
        return slot.captured(seedConfig)
    }

    // ------------------------------------------------------------------------
    // 1. Non-disconnect event → no-op
    // ------------------------------------------------------------------------
    @Test
    fun `connected event is ignored`() = runTest {
        val module = createModule()
        val device = managedDevice(config(musicVolume = 0.5f))
        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

        module.handle(DeviceEvent.Connected(device))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // 2. volumeSaveOnDisconnect=false → no-op
    // ------------------------------------------------------------------------
    @Test
    fun `save on disconnect disabled - no-op`() = runTest {
        val module = createModule()
        val device = managedDevice(
            config(musicVolume = 0.5f, volumeSaveOnDisconnect = false),
        )
        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

        module.handle(DeviceEvent.Disconnected(device))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // 3. Drift regression — the exact logcat values
    // ------------------------------------------------------------------------
    @Test
    fun `drift regression - stored 042344 at level 11 of 25 does not write`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.42344147f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 11, max = 25)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.musicVolume shouldBe 0.42344147f
    }

    // ------------------------------------------------------------------------
    // 4. Ringer NORMAL, stored 0.5, hardware level differs → writes new percent
    // ------------------------------------------------------------------------
    @Test
    fun `ringer normal music volume changed writes new percent`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 11, max = 25)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.musicVolume shouldBe (11f / 25f)
    }

    // ------------------------------------------------------------------------
    // 5. Ringer VIBRATE, stored Vibrate sentinel → structurally equal, no-op
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer with stored vibrate ringtone is a no-op`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = -3.0f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        // Mode structurally matches stored Vibrate → preserved, not rewritten.
        result.ringVolume shouldBe -3.0f
    }

    // ------------------------------------------------------------------------
    // 6. Ringer VIBRATE, stored Normal(0.48) ringtone → preserves stored value
    //    (ringer mode is system-wide and may have been set by another device's
    //     connect modules — disconnect-save can't distinguish that from a user
    //     change, so it preserves the DB value and lets BVM UI or
    //     volumeObserving handle ringer mode persistence)
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer with stored normal ringtone preserves stored value`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = 0.48f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe 0.48f
    }

    // ------------------------------------------------------------------------
    // 7. Ringer NORMAL, ringtone drift suppression
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer with matching ringtone level does not drift`() = runTest {
        val module = createModule()
        // 0.48 * 7 = 3.36 → level 3 → saving it back would give 3/7 = 0.4285
        val cfg = config(ringVolume = 0.48f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 3, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe 0.48f
    }

    // ------------------------------------------------------------------------
    // 8. Ringer VIBRATE, stored notification=0.19, hardware 0 → preserved
    //    (Pixel-style platform coupling: STREAM_NOTIFICATION clamps to 0 in
    //     vibrate. Preserve the stored value rather than zero it out.)
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer with notification hardware zero preserves stored`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        mockStream(AudioStream.Id.STREAM_NOTIFICATION, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.notificationVolume shouldBe 0.19f
    }

    // ------------------------------------------------------------------------
    // 8b. Ringer VIBRATE, non-coupling device, notification hardware > 0 →
    //     captured (user-visible independent control must be persisted)
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer with non-zero notification hardware captures user change`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        // Non-coupling device lets the user set notification independently of
        // ringer mode; hardware reports level 5 / 7.
        mockStream(AudioStream.Id.STREAM_NOTIFICATION, current = 5, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.notificationVolume shouldBe (5f / 7f)
    }

    // ------------------------------------------------------------------------
    // 8c. KNOWN LIMITATION (documented, intentional):
    //     Ringer VIBRATE, non-coupling device, user sets notification to 0,
    //     hardware=0, stored>0 → we cannot distinguish this from Pixel's
    //     coupling-clamp on a single read, so we preserve stored.
    // ------------------------------------------------------------------------
    @Test
    fun `known limitation - non-coupling user zero in vibrate is not captured`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        // Hypothetical non-coupling device where the user has deliberately
        // set notification to 0 while in vibrate.
        mockStream(AudioStream.Id.STREAM_NOTIFICATION, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        // Pixel-coupling case dominates the heuristic: preserve stored. The
        // user's intentional 0 is not captured — this is a documented
        // tradeoff against the (worse) behavior of destroying legitimate
        // stored notification values on Pixel devices.
        result.notificationVolume shouldBe 0.19f
    }

    // ------------------------------------------------------------------------
    // 9. Ringer NORMAL, notification level differs → writes new percent
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer notification level differs writes new percent`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        // 0.19 * 7 = 1.33 → saved level 1; simulate user bumped it to 5
        mockStream(AudioStream.Id.STREAM_NOTIFICATION, current = 5, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.notificationVolume shouldBe (5f / 7f)
    }

    // ------------------------------------------------------------------------
    // 10. Ringer SILENT, stored Normal ringtone → preserves stored value
    //     (ringer mode is system-wide shared state, not per-device)
    // ------------------------------------------------------------------------
    @Test
    fun `silent ringer with stored normal ringtone preserves stored value`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.SILENT
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe 0.5f
    }

    // ------------------------------------------------------------------------
    // 10b. Ringer SILENT, stored Silent sentinel → no-op (structural match)
    // ------------------------------------------------------------------------
    @Test
    fun `silent ringer with stored silent sentinel is a no-op`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = VolumeMode.LEGACY_SILENT_VALUE)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.SILENT
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe VolumeMode.LEGACY_SILENT_VALUE
    }

    // ------------------------------------------------------------------------
    // 11. Ringer NORMAL, stored Vibrate sentinel → user switched out of vibrate;
    //     handleRingerMode didn't fire for this device → write Normal(current)
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer with stored vibrate sentinel writes current normal`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = -3.0f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 4, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe (4f / 7f)
    }

    // ------------------------------------------------------------------------
    // 12. Normal(0f) edge case for RINGTONE in NORMAL mode
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer stored normal zero with hardware zero is no-op`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = 0f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_RINGTONE, current = 0, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.ringVolume shouldBe 0f
    }

    // ------------------------------------------------------------------------
    // 13. Corrupt stored value → heal with sanitized Normal
    // ------------------------------------------------------------------------
    @Test
    fun `corrupt stored alarm value is healed`() = runTest {
        val module = createModule()
        // 5.5 is outside [0, 1] and not a -2/-3 sentinel → VolumeMode.fromFloat returns null
        val cfg = config(alarmVolume = 5.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_ALARM, current = 3, max = 7)

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.alarmVolume shouldBe (3f / 7f)
    }

    // ------------------------------------------------------------------------
    // 14. Pathological hardware: maxLevel = 0 → skip stream
    // ------------------------------------------------------------------------
    @Test
    fun `max level zero is skipped without exception`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 0, max = 0)

        module.handle(DeviceEvent.Disconnected(device))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // 15. Pathological hardware: currentLevel > maxLevel → skip stream
    // ------------------------------------------------------------------------
    @Test
    fun `current level above max is skipped without exception`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 30, max = 25)

        module.handle(DeviceEvent.Disconnected(device))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // 16. Mixed streams: only MUSIC was user-changed, everything else preserved
    // ------------------------------------------------------------------------
    @Test
    fun `mixed streams only music written`() = runTest {
        val module = createModule()
        val cfg = config(
            musicVolume = 0.5f,           // user changed
            alarmVolume = 0.5714286f,     // level 4 of 7 — unchanged
            ringVolume = -3.0f,           // vibrate, ringer is vibrate → skipped
            notificationVolume = 0.19f,   // vibrate, ringer is vibrate → skipped
        )
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 20, max = 25)
        mockStream(AudioStream.Id.STREAM_ALARM, current = 4, max = 7)
        // RINGTONE and NOTIFICATION skipped before hardware read, but mockk
        // relaxed returns 0 anyway if they are accessed.

        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)

        result.musicVolume shouldBe (20f / 25f)
        result.alarmVolume shouldBe 0.5714286f
        result.ringVolume shouldBe -3.0f
        result.notificationVolume shouldBe 0.19f
    }

    // ------------------------------------------------------------------------
    // 17. Stale snapshot race — event.device.config disagrees with oldConfig
    // ------------------------------------------------------------------------
    @Test
    fun `stale snapshot race uses oldConfig not event device`() = runTest {
        val module = createModule()
        // event snapshot says 0.5 (stale)
        val stale = config(musicVolume = 0.5f)
        val device = managedDevice(stale)
        // DB-fresh oldConfig says 0.9 — VolumeUpdateModule wrote it between
        // dispatch and this module's turn.
        val fresh = config(musicVolume = 0.9f)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        // Hardware matches the fresh value: 0.9 * 25 = 22.5 → level 22 or 23.
        // round(0.9 * 25) = 23. Use 23 so the drift check against fresh sees a
        // match; against the stale 0.5 (round→13) it would mismatch and write.
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 23, max = 25)

        val result = runTransform(module, DeviceEvent.Disconnected(device), fresh)

        // If the module had compared against the stale snapshot, it would have
        // overwritten with 23/25 = 0.92. Comparing against fresh oldConfig, the
        // level matches and we preserve 0.9.
        result.musicVolume shouldBe 0.9f
    }

    // ------------------------------------------------------------------------
    // 18. Owner disconnects (wasInOwnerGroup=true) → saves volumes
    // ------------------------------------------------------------------------
    @Test
    fun `owner disconnect with wasInOwnerGroup true saves volumes`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 20, max = 25)

        val disconnectResult = eu.darken.bluemusic.monitor.core.ownership.DisconnectResult(
            wasInOwnerGroup = true,
            ownerGroupBefore = listOf(address),
            ownerGroupAfter = null,
        )

        module.handle(DeviceEvent.Disconnected(device, disconnectResult = disconnectResult))

        coVerify(exactly = 1) { deviceRepo.updateDevice(address, any()) }
    }

    // ------------------------------------------------------------------------
    // 19. Non-owner disconnects (wasInOwnerGroup=false) → skips save
    // ------------------------------------------------------------------------
    @Test
    fun `non-owner disconnect with wasInOwnerGroup false skips save`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 20, max = 25)

        val disconnectResult = eu.darken.bluemusic.monitor.core.ownership.DisconnectResult(
            wasInOwnerGroup = false,
            ownerGroupBefore = listOf("OTHER:ADDRESS:00:00"),
            ownerGroupAfter = listOf("OTHER:ADDRESS:00:00"),
        )

        module.handle(DeviceEvent.Disconnected(device, disconnectResult = disconnectResult))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // 20. One bud from owner pair disconnects → saves (was in owner group)
    // ------------------------------------------------------------------------
    @Test
    fun `one bud from owner pair disconnects saves volumes`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 20, max = 25)

        val siblingAddress = "AA:BB:CC:DD:EE:02"
        val disconnectResult = eu.darken.bluemusic.monitor.core.ownership.DisconnectResult(
            wasInOwnerGroup = true,
            ownerGroupBefore = listOf(address, siblingAddress),
            ownerGroupAfter = listOf(siblingAddress),
        )

        module.handle(DeviceEvent.Disconnected(device, disconnectResult = disconnectResult))

        coVerify(exactly = 1) { deviceRepo.updateDevice(address, any()) }
    }

    // ------------------------------------------------------------------------
    // 21. No notification-policy permission is irrelevant to the disconnect path
    // ------------------------------------------------------------------------
    @Test
    fun `module does not depend on notification policy access`() = runTest {
        // The disconnect path only reads via AudioManager.getStreamVolume,
        // which does not require notification policy access. There is no
        // PermissionHelper dependency to inject at all — this is the test:
        // constructing and running the module must succeed without one.
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        val device = managedDevice(cfg)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        mockStream(AudioStream.Id.STREAM_MUSIC, current = 13, max = 25)

        // Should not throw. 0.5 * 25 = 12.5 → round → 13. Drift-suppressed.
        val result = runTransform(module, DeviceEvent.Disconnected(device), cfg)
        result.musicVolume shouldBe 0.5f
    }
}
