package eu.darken.bluemusic.main.core

fun Settings.isOnboardingCompleted(): Boolean {
    // The original method has inverted logic - true means show onboarding
    return !isShowOnboarding
}

fun Settings.setOnboardingCompleted(completed: Boolean) {
    // Invert the logic to match the original implementation
    isShowOnboarding = !completed
}