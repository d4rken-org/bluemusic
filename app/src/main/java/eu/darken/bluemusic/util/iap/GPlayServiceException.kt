package eu.darken.bluemusic.util.iap

class GPlayServiceException(cause: Throwable)
    : Exception("Google Play services are unavailable.", cause)