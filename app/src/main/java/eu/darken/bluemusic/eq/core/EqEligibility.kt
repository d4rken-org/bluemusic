package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the equalizer can do anything at all right now: the device has a usable engine and the
 * user is entitled to the feature.
 */
@Singleton
class EqEligibility @Inject constructor(
    private val eqCapabilities: EqCapabilities,
    private val upgradeRepo: UpgradeRepo,
) {

    val operational: Flow<Boolean> = combine(
        eqCapabilities.capabilities.onStart { eqCapabilities.refreshIfNeeded() }.map { it != null },
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { hasEngine, isPro ->
        hasEngine && isPro
    }.distinctUntilChanged()

    suspend fun isOperational(): Boolean = operational.first()
}
