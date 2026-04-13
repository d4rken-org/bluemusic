package eu.darken.bluemusic.upgrade.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@Composable
fun UpgradeScreenHost(vm: UpgradeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var showRestoreFailedDialog by remember { mutableStateOf(false) }

    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()
    UpgradeScreen(
        state = state,
        onNavigateBack = { vm.navUp() },
        onGoIap = { vm.onGoIap(context as android.app.Activity) },
        onGoSubscription = { vm.onGoSubscription(context as android.app.Activity) },
        onGoSubscriptionTrial = { vm.onGoSubscriptionTrial(context as android.app.Activity) },
        onRestorePurchase = { vm.restorePurchase() },
    )

    LaunchedEffect(Unit) {
        vm.events.collect { event: UpgradeEvents ->
            when (event) {
                UpgradeEvents.RestoreFailed -> {
                    showRestoreFailedDialog = true
                }
            }
        }
    }

    if (showRestoreFailedDialog) {
        RestoreFailedDialog(
            onDismiss = { showRestoreFailedDialog = false }
        )
    }
}

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State?,
    onNavigateBack: () -> Unit,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onRestorePurchase: () -> Unit,
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
    ) {
        PricingContent(
            state = state,
            onGoIap = onGoIap,
            onGoSubscription = onGoSubscription,
            onGoSubscriptionTrial = onGoSubscriptionTrial,
            onRestorePurchase = onRestorePurchase,
        )
    }
}

@Composable
private fun PricingContent(
    state: UpgradeViewModel.State?,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    if (state == null) {
        CircularProgressIndicator(modifier = Modifier.padding(vertical = 12.dp))
        return
    }

    val hasSubscriptionOption = state.subState.available || state.trialState.available
    val hasIapOption = state.iapState.available

    if (state.trialState.available || state.subState.available) {
        val onPrimaryAction = if (state.trialState.available) onGoSubscriptionTrial else onGoSubscription
        val primaryLabel = if (state.trialState.available) {
            stringResource(R.string.upgrade_screen_subscription_trial_action)
        } else {
            stringResource(R.string.upgrade_screen_subscription_action)
        }

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = null,
            )
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        state.subState.formattedPrice?.let { formattedPrice ->
            Text(
                text = stringResource(R.string.upgrade_screen_subscription_action_hint, formattedPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }

    if (hasSubscriptionOption && hasIapOption) {
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (hasIapOption) {
        if (hasSubscriptionOption) {
            FilledTonalButton(
                onClick = onGoIap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.upgrade_screen_iap_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Button(
                onClick = onGoIap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.upgrade_screen_iap_action),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        state.iapState.formattedPrice?.let { formattedPrice ->
            Text(
                text = stringResource(R.string.upgrade_screen_iap_action_hint, formattedPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }

    if (!hasSubscriptionOption && !hasIapOption) {
        Button(
            onClick = onGoIap,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.general_upgrade_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onRestorePurchase,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
    }

    Text(
        text = stringResource(R.string.upgrade_screen_restore_purchase_message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Preview2
@Composable
fun UpgradeScreenPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = UpgradeViewModel.State(
                iapState = UpgradeViewModel.State.Iap(
                    available = true,
                    formattedPrice = "$4.99",
                ),
                subState = UpgradeViewModel.State.Sub(
                    available = true,
                    formattedPrice = "$2.99",
                ),
                trialState = UpgradeViewModel.State.Trial(
                    available = true,
                    formattedPrice = "$2.99"
                ),
            ),
            onNavigateBack = {},
            onGoIap = {},
            onGoSubscription = {},
            onGoSubscriptionTrial = {},
            onRestorePurchase = {},
        )
    }
}


@Composable
fun RestoreFailedDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.general_error_label),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        text = {
            Text(
                text = """
                    ${stringResource(R.string.upgrade_screen_restore_purchase_message)}
                    
                    ${stringResource(R.string.upgrade_screen_restore_troubleshooting_msg)}
                    
                    ${stringResource(R.string.upgrade_screen_restore_sync_patience_hint)}
                    
                    ${stringResource(R.string.upgrade_screen_restore_multiaccount_hint)}
                """.trimIndent()
            )
        }
    )
}

@Preview2
@Composable
fun RestoreFailedDialogPreview() {
    PreviewWrapper {
        RestoreFailedDialog(
            onDismiss = {}
        )
    }
}
