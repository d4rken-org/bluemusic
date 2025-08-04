package eu.darken.bluemusic.upgrade.core

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FossUpgrade(
    @SerialName("upgradedAt") @Contextual val upgradedAt: Instant,
    @SerialName("upgradeType") val upgradeType: Type,
) {
    @Serializable
    enum class Type {
        @SerialName("GITHUB_SPONSORS") GITHUB_SPONSORS,
        ;
    }
}