package eu.darken.bluemusic.common.error

import eu.darken.bluemusic.common.ca.caString
import eu.darken.bluemusic.common.ca.toCaString

class MissingPermissionException(
    val permission: String,
) : SecurityException(), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "ReadException".toCaString(),
        description = caString { cx ->
            "Missing permissions: $permission"
        }
    )
}