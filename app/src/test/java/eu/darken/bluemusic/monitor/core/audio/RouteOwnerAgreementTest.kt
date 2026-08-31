package eu.darken.bluemusic.monitor.core.audio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RouteOwnerAgreementTest : BaseTest() {

    private val owner = "AA:BB:CC:DD:EE:FF"

    private fun route(isBluetooth: Boolean?, vararg addresses: String) = VolumeTool.MediaRoute(
        isBluetooth = isBluetooth,
        addresses = addresses.toSet(),
        description = "test-route",
    )

    @Test
    fun `an unclassifiable route is unknown for both device kinds`() {
        routeVerdict(
            route(null),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.UNKNOWN
        routeVerdict(
            route(null),
            isPhoneSpeaker = true,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.UNKNOWN
    }

    @Test
    fun `a missing route is unknown for both device kinds`() {
        routeVerdict(
            null,
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.UNKNOWN
        routeVerdict(
            null,
            isPhoneSpeaker = true,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.UNKNOWN
    }

    @Test
    fun `bluetooth device disagrees with a non-bluetooth route`() {
        routeVerdict(
            route(false),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.DISAGREE
    }

    @Test
    fun `bluetooth device agrees when the route names it`() {
        routeVerdict(
            route(true, owner),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.AGREE
    }

    @Test
    fun `bluetooth device agrees when the route has no usable addresses`() {
        routeVerdict(
            route(true),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.AGREE
        routeVerdict(
            route(true, "", "   "),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.AGREE
    }

    @Test
    fun `bluetooth device disagrees when the route names a different managed device`() {
        routeVerdict(
            route(true, "11:22:33:44:55:66"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner, "11:22:33:44:55:66"),
        ) shouldBe RouteVerdict.DISAGREE
    }

    @Test
    fun `bluetooth device agrees when the route names nothing BVM manages`() {
        // LE Audio set members and hearing aids can report an address the ACL
        // broadcasts never carried; it identifies no other device, so it can't
        // contradict the owner.
        routeVerdict(
            route(true, "11:22:33:44:55:66"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.AGREE
    }

    @Test
    fun `route naming another member of the owner group agrees`() {
        // Grouped earbuds: the left bud is routed, the right bud reports the change.
        routeVerdict(
            route(true, "11:22:33:44:55:66"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner, "11:22:33:44:55:66"),
            knownAddresses = setOf(owner, "11:22:33:44:55:66"),
        ) shouldBe RouteVerdict.AGREE
    }

    @Test
    fun `address comparison ignores case`() {
        routeVerdict(
            route(true, "aa:bb:cc:dd:ee:ff"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner),
        ) shouldBe RouteVerdict.AGREE
    }

    @Test
    fun `known address comparison ignores case`() {
        routeVerdict(
            route(true, "11:22:33:44:55:66"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner, "11:22:33:44:55:AA"),
        ) shouldBe RouteVerdict.AGREE
        routeVerdict(
            route(true, "11:22:33:44:55:aa"),
            isPhoneSpeaker = false,
            ownerAddresses = setOf(owner),
            knownAddresses = setOf(owner, "11:22:33:44:55:AA"),
        ) shouldBe RouteVerdict.DISAGREE
    }

    @Test
    fun `phone speaker disagrees with a bluetooth route`() {
        routeVerdict(
            route(true, owner),
            isPhoneSpeaker = true,
            ownerAddresses = setOf("speaker"),
            knownAddresses = setOf("speaker", owner),
        ) shouldBe RouteVerdict.DISAGREE
    }

    @Test
    fun `phone speaker agrees with a non-bluetooth route`() {
        routeVerdict(
            route(false),
            isPhoneSpeaker = true,
            ownerAddresses = setOf("speaker"),
            knownAddresses = setOf("speaker"),
        ) shouldBe RouteVerdict.AGREE
    }
}
