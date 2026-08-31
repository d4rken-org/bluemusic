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
 * Route says BT_A2DP 'AA:BB' while the owner group is ["AA:BB"] -> AGREE.
 * Route says SPEAKER while the owner group is ["AA:BB"] -> DISAGREE.
 * Route says BT_A2DP 'CC:DD' while the owner group is ["AA:BB"] -> DISAGREE.
 *
 * UNKNOWN whenever the route can't classify itself, so an uninformative query never blocks.
 */
fun routeVerdict(
    route: VolumeTool.MediaRoute?,
    isPhoneSpeaker: Boolean,
    ownerAddresses: Set<String>,
): RouteVerdict {
    if (route == null) return RouteVerdict.UNKNOWN
    val routedToBluetooth = route.isBluetooth ?: return RouteVerdict.UNKNOWN

    if (isPhoneSpeaker) return if (routedToBluetooth) RouteVerdict.DISAGREE else RouteVerdict.AGREE

    if (!routedToBluetooth) return RouteVerdict.DISAGREE

    val routed = route.addresses.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    if (routed.isEmpty()) return RouteVerdict.AGREE

    val owners = ownerAddresses.map { it.lowercase() }.toSet()
    return if (routed.any { it in owners }) RouteVerdict.AGREE else RouteVerdict.DISAGREE
}
