package eu.darken.bluemusic.monitor.core.audio

enum class RouteVerdict {
    AGREE,
    DISAGREE,
    UNKNOWN,
    ;
}

/**
 * Does the media route agree that [ownerAddresses] (or the phone speaker) owns what is playing?
 *
 * [knownAddresses] are all addresses BVM manages, owners included.
 *
 * Route BT_A2DP 'AA:BB', owners ["AA:BB"] -> AGREE.
 * Route SPEAKER, owners ["AA:BB"] -> DISAGREE.
 * Route BT_A2DP 'CC:DD', owners ["AA:BB"], known ["AA:BB", "CC:DD"] -> DISAGREE.
 * Route BT_A2DP 'CC:DD', owners ["AA:BB"], known ["AA:BB"] -> AGREE.
 *
 * UNKNOWN whenever the route can't classify itself, so an uninformative query never blocks.
 */
fun routeVerdict(
    route: VolumeTool.MediaRoute?,
    isPhoneSpeaker: Boolean,
    ownerAddresses: Set<String>,
    knownAddresses: Set<String>,
): RouteVerdict {
    if (route == null) return RouteVerdict.UNKNOWN
    val routedToBluetooth = route.isBluetooth ?: return RouteVerdict.UNKNOWN

    if (isPhoneSpeaker) return if (routedToBluetooth) RouteVerdict.DISAGREE else RouteVerdict.AGREE

    if (!routedToBluetooth) return RouteVerdict.DISAGREE

    val routed = route.addresses.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    if (routed.isEmpty()) return RouteVerdict.AGREE

    val owners = ownerAddresses.map { it.lowercase() }.toSet()
    if (routed.any { it in owners }) return RouteVerdict.AGREE

    // Owner addresses come from ACL broadcasts, the route address comes from the audio
    // framework: an LE Audio set member or a hearing aid can report a third address that
    // belongs to no device BVM knows. That says nothing, so it counts as agreement.
    val others = knownAddresses.map { it.lowercase() }.toSet() - owners
    return if (routed.any { it in others }) RouteVerdict.DISAGREE else RouteVerdict.AGREE
}
