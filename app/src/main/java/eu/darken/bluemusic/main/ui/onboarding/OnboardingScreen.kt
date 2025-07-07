package eu.darken.bluemusic.main.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.main.ui.onboarding.OnboardingViewModel.State.Page
import eu.darken.butler.main.ui.onboarding.pages.BetaPage
import eu.darken.bluemusic.main.ui.onboarding.pages.PrivacyPage
import eu.darken.butler.main.ui.onboarding.pages.WelcomePage
import kotlinx.coroutines.launch


@Composable
fun OnboardingScreenHost(vm: OnboardingViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { state ->
        OnboardingScreen(
            state = state,
            onUpdateCheckChange = { vm.setUpdateCheckEnabled(it) },
            onMotdCheckChange = { vm.setMotdCheckEnabled(it) },
            onReadPrivacyPolicy = { vm.readPrivacyPolicy() },
            onFinishOnboarding = vm::completeOnboarding,
        )
    }
}


@Composable
private fun OnboardingScreen(
    state: OnboardingViewModel.State,
    onUpdateCheckChange: (Boolean) -> Unit,
    onMotdCheckChange: (Boolean) -> Unit,
    onReadPrivacyPolicy: () -> Unit,
    onFinishOnboarding: () -> Unit,
) {

    val pagerState =
        rememberPagerState(
            initialPage = state.startPage.ordinal,
            pageCount = { Page.entries.size }
        )
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false
        ) { page ->
            when (Page.entries[page]) {
                Page.WELCOME ->
                    WelcomePage(
                        onContinue = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    )

                Page.BETA ->
                    BetaPage(
                        onContinue = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    )

                Page.PRIVACY ->
                    PrivacyPage(
                        onReadPrivacyPolicy = onReadPrivacyPolicy,
                        onAccept = { onFinishOnboarding() }
                    )
            }
        }
    }
}

@Preview
@Composable
private fun IntroScreenPreview() {
    PreviewWrapper {
        OnboardingScreen(
            state = OnboardingViewModel.State(),
            onUpdateCheckChange = {},
            onMotdCheckChange = {},
            onReadPrivacyPolicy = {},
            onFinishOnboarding = {},
        )
    }
}
