package eu.darken.bluemusic.common.error

import androidx.annotation.StringRes

open class UserFacingException(
    override val message: String,
    @StringRes val messageRes: Int? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)