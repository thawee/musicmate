package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.calibration.CarProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CarRouteTest {
    private fun profile(id: String, address: String?, kind: String?) = CarProfile(
        id = id,
        name = id,
        createdAtMillis = 0,
        bluetoothAddress = address,
        bands = emptyList(),
        fromHz = 45.0,
        measuredDb = emptyList(),
        targetDb = emptyList(),
        predictedDb = emptyList(),
        micSource = "UNPROCESSED",
        approximate = false,
        kind = kind
    )

    private val mux = profile("mu-x", "AA:BB:CC:DD:EE:01", CarProfile.KIND_CAR)
    private val al5 = profile("al5", "AA:BB:CC:DD:EE:02", CarProfile.KIND_SPEAKER)

    @Test
    fun usbWinsOverEverythingElse() {
        val route = CarRoute.of(listOf(
            Triple(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "1:ap:5mixp:0", null),
            Triple(AudioDeviceInfo.TYPE_USB_HEADSET, "card=1", "DAC")
        ))
        assertSame(CarRoute.Usb, route)
    }

    @Test
    fun remoteSubmixIsAndroidAutoWhateverItsAddress() {
        val first = CarRoute.of(listOf(Triple(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "937771686:ap:5mixp:0", null)))
        val next = CarRoute.of(listOf(Triple(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "-1085996066:ap:6mixp:0", null)))
        assertSame(CarRoute.AndroidAuto, first)
        assertEquals(first.key, next.key)
    }

    @Test
    fun bluetoothRouteKeysOnTheAddressInAnyCase() {
        val route = CarRoute.of(listOf(Triple(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "aa:bb:cc:dd:ee:02", "AL5")))
        route as CarRoute.Bluetooth
        assertEquals("bt:AA:BB:CC:DD:EE:02", route.key)
        assertEquals("AL5", route.name)
    }

    @Test
    fun speakerAndOtherOutputsAreOther() {
        assertSame(CarRoute.Other, CarRoute.of(listOf(Triple(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "", null))))
        assertSame(CarRoute.Other, CarRoute.of(listOf(Triple(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "", "No address"))))
        assertSame(CarRoute.Other, CarRoute.of(emptyList()))
    }

    @Test
    fun bluetoothPicksTheSpeakerByAddress() {
        val route = CarRoute.Bluetooth("aa:bb:cc:dd:ee:02", "AL5")
        assertEquals(al5, CarRoute.choose(route, listOf(mux, al5), mux.bluetoothAddress))
    }

    @Test
    fun unknownBluetoothSpeakerGetsNoCorrection() {
        val route = CarRoute.Bluetooth("11:22:33:44:55:66", "Earbuds")
        assertNull(CarRoute.choose(route, listOf(mux, al5), null))
    }

    @Test
    fun androidAutoPicksTheCarOfTheLastHandshake() {
        val older = profile("old-car", "AA:BB:CC:DD:EE:03", null)
        assertEquals(older, CarRoute.choose(CarRoute.AndroidAuto, listOf(mux, older, al5), "aa:bb:cc:dd:ee:03"))
    }

    @Test
    fun androidAutoFallsBackToTheOnlyCar() {
        assertEquals(mux, CarRoute.choose(CarRoute.AndroidAuto, listOf(mux, al5), null))
        assertEquals(mux, CarRoute.choose(CarRoute.AndroidAuto, listOf(mux, al5), "11:22:33:44:55:66"))
    }

    @Test
    fun androidAutoWithTwoCarsAndNoHandshakeGetsNoCorrection() {
        val second = profile("second", "AA:BB:CC:DD:EE:04", CarProfile.KIND_CAR)
        assertNull(CarRoute.choose(CarRoute.AndroidAuto, listOf(mux, second, al5), null))
    }

    @Test
    fun otherRoutesGetNoCorrection() {
        assertNull(CarRoute.choose(CarRoute.Other, listOf(mux, al5), mux.bluetoothAddress))
    }
}
