package eu.darken.bluemusic.upgrade.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun UpgradeScreenHost(
    manage: Boolean,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)

    val snackbarHostState = remember { SnackbarHostState() }
    val tooFastMessage = stringResource(R.string.upgrade_screen_sponsor_too_fast)

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPaused() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is UpgradeEvent.SpendMoreTime -> snackbarHostState.showSnackbar(message = tooFastMessage)
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle()

    // Show the supporter-status view whenever the user is already a supporter (independent of the
    // manage flag); otherwise the sponsor pitch.
    if (state?.isSupporter == true) {
        SupporterStatusScreen(
            supporterSince = state?.supporterSince,
            onNavigateBack = { vm.navUp() },
            onVisitSponsors = { vm.openSponsorPage() },
        )
    } else {
        UpgradeScreen(
            onNavigateBack = { vm.navUp() },
            onSponsorClick = { vm.openSponsor() },
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
fun UpgradeScreen(
    onNavigateBack: () -> Unit,
    onSponsorClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val benefits = listOf(
        UpgradeBenefitItem(Icons.TwoTone.Devices, stringResource(R.string.upgrade_benefit_unlimited_devices)),
        UpgradeBenefitItem(Icons.TwoTone.PlayCircle, stringResource(R.string.upgrade_benefit_connection_actions)),
        UpgradeBenefitItem(Icons.TwoTone.Palette, stringResource(R.string.upgrade_benefit_theme_customization)),
        UpgradeBenefitItem(Icons.TwoTone.Tune, stringResource(R.string.upgrade_benefit_power_controls)),
        UpgradeBenefitItem(Icons.TwoTone.Favorite, stringResource(R.string.upgrade_benefit_support)),
    )

    UpgradeScreenScaffold(
        title = stringResource(R.string.app_name_upgraded),
        postfix = stringResource(R.string.app_name_upgrade_postfix),
        preamble = stringResource(R.string.upgrade_screen_preamble),
        benefitTitle = stringResource(R.string.upgrade_screen_why_title),
        benefits = benefits,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) {
        Button(
            onClick = onSponsorClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(imageVector = Icons.TwoTone.Favorite, contentDescription = null)
            Text(
                text = stringResource(R.string.upgrade_screen_sponsor_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = stringResource(R.string.upgrade_screen_sponsor_action_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
fun SupporterStatusScreen(
    supporterSince: Instant?,
    onNavigateBack: () -> Unit,
    onVisitSponsors: () -> Unit,
) {
    UpgradeScreenShell(
        title = stringResource(R.string.app_name_upgraded),
        postfix = stringResource(R.string.app_name_upgrade_postfix),
        onNavigateBack = onNavigateBack,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.TwoTone.Favorite, contentDescription = null)
                    Text(
                        text = stringResource(R.string.upgrade_screen_supporter_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_supporter_status_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                supporterSince?.let { since ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.upgrade_screen_supporter_since, SUPPORTER_DATE_FORMAT.format(since)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onVisitSponsors,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(imageVector = Icons.TwoTone.Favorite, contentDescription = null)
            Text(
                text = stringResource(R.string.upgrade_screen_supporter_visit_action),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private val SUPPORTER_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@Preview2
@Composable
private fun UpgradeScreenPreview() {
    PreviewWrapper {
        UpgradeScreen(onNavigateBack = {}, onSponsorClick = {})
    }
}

@Preview2
@Composable
private fun SupporterStatusScreenPreview() {
    PreviewWrapper {
        SupporterStatusScreen(
            supporterSince = Instant.ofEpochMilli(1_700_000_000_000L),
            onNavigateBack = {},
            onVisitSponsors = {},
        )
    }
}
