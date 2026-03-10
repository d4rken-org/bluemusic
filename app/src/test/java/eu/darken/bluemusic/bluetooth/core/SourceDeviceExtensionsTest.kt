package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SourceDeviceExtensionsTest : BaseTest() {

    private fun bluetoothClass(deviceClass: Int, majorDeviceClass: Int): BluetoothClass = mockk {
        every { this@mockk.deviceClass } returns deviceClass
        every { this@mockk.majorDeviceClass } returns majorDeviceClass
    }

    @Test
    fun `null BluetoothClass returns UNKNOWN`() {
        val btClass: BluetoothClass? = null
        btClass.toType() shouldBe SourceDevice.Type.UNKNOWN
    }

    @Test
    fun `headphones device class`() {
        bluetoothClass(
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.HEADPHONES
    }

    @Test
    fun `car audio device class`() {
        bluetoothClass(
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.CAR_AUDIO
    }

    @Test
    fun `loudspeaker device class`() {
        bluetoothClass(
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.PORTABLE_SPEAKER
    }

    @Test
    fun `wearable headset device class`() {
        bluetoothClass(
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.HEADSET
    }

    @Test
    fun `portable audio device class`() {
        bluetoothClass(
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.PORTABLE_SPEAKER
    }

    @Test
    fun `laptop device class`() {
        bluetoothClass(
            BluetoothClass.Device.COMPUTER_LAPTOP,
            BluetoothClass.Device.Major.COMPUTER
        ).toType() shouldBe SourceDevice.Type.COMPUTER
    }

    @Test
    fun `desktop device class`() {
        bluetoothClass(
            BluetoothClass.Device.COMPUTER_DESKTOP,
            BluetoothClass.Device.Major.COMPUTER
        ).toType() shouldBe SourceDevice.Type.COMPUTER
    }

    @Test
    fun `smart phone device class`() {
        bluetoothClass(
            BluetoothClass.Device.PHONE_SMART,
            BluetoothClass.Device.Major.PHONE
        ).toType() shouldBe SourceDevice.Type.SMARTPHONE
    }

    @Test
    fun `cellular phone device class`() {
        bluetoothClass(
            BluetoothClass.Device.PHONE_CELLULAR,
            BluetoothClass.Device.Major.PHONE
        ).toType() shouldBe SourceDevice.Type.SMARTPHONE
    }

    @Test
    fun `wrist watch device class`() {
        bluetoothClass(
            BluetoothClass.Device.WEARABLE_WRIST_WATCH,
            BluetoothClass.Device.Major.WEARABLE
        ).toType() shouldBe SourceDevice.Type.WATCH
    }

    // region Major class fallback

    @Test
    fun `audio video major class fallback to HEADPHONES`() {
        bluetoothClass(
            0x999, // Unknown specific device class
            BluetoothClass.Device.Major.AUDIO_VIDEO
        ).toType() shouldBe SourceDevice.Type.HEADPHONES
    }

    @Test
    fun `computer major class fallback to COMPUTER`() {
        bluetoothClass(
            0x999,
            BluetoothClass.Device.Major.COMPUTER
        ).toType() shouldBe SourceDevice.Type.COMPUTER
    }

    @Test
    fun `phone major class fallback to SMARTPHONE`() {
        bluetoothClass(
            0x999,
            BluetoothClass.Device.Major.PHONE
        ).toType() shouldBe SourceDevice.Type.SMARTPHONE
    }

    @Test
    fun `wearable major class fallback to WATCH`() {
        bluetoothClass(
            0x999,
            BluetoothClass.Device.Major.WEARABLE
        ).toType() shouldBe SourceDevice.Type.WATCH
    }

    // endregion

    @Test
    fun `unknown device class and major class returns UNKNOWN`() {
        bluetoothClass(
            0x999,
            0x999
        ).toType() shouldBe SourceDevice.Type.UNKNOWN
    }
}
