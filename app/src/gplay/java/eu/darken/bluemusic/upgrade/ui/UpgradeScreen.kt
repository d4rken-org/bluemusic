package eu.darken.bluemusic.upgrade.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler


@Composable
fun UpgradeScreenHost(
    manage: Boolean,
    vm: UpgradeViewModel = hiltViewModel(
        key = "upgrade-$manage",
        creationCallback = { factory: UpgradeViewModel.Factory -> factory.create(manage = manage) },
    ),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val restoreSucceededMessage = stringResource(R.string.upgrade_screen_thanks_toast)

    var showRestoreFailedDialog by remember { mutableStateOf(false) }
    var showStillRenewingDialog by remember { mutableStateOf(false) }
    var showCheckFailedDialog by remember { mutableStateOf(false) }

    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()
    UpgradeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = { vm.navUp() },
        onGoIap = { vm.onGoIap(context as Activity) },
        onGoSubscription = { vm.onGoSubscription(context as Activity) },
        onGoSubscriptionTrial = { vm.onGoSubscriptionTrial(context as Activity) },
        onManageSubscription = { vm.onManageSubscription() },
        onRestorePurchase = { vm.restorePurchase() },
        onRetry = { vm.onRetry() },
    )

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreSucceeded -> snackbarHostState.showSnackbar(restoreSucceededMessage)
                UpgradeEvents.RestoreFailed -> showRestoreFailedDialog = true
                UpgradeEvents.SubscriptionStillRenewing -> showStillRenewingDialog = true
                UpgradeEvents.SubscriptionCheckFailed -> showCheckFailedDialog = true
            }
        }
    }

    if (showRestoreFailedDialog) {
        RestoreFailedDialog(onDismiss = { showRestoreFailedDialog = false })
    }
    if (showStillRenewingDialog) {
        SubscriptionStillRenewingDialog(
            onManageSubscription = { vm.onManageSubscription() },
            onDismiss = { showStillRenewingDialog = false },
        )
    }
    if (showCheckFailedDialog) {
        SubscriptionCheckFailedDialog(onDismiss = { showCheckFailedDialog = false })
    }
}

@Composable
fun UpgradeScreen(
    state: UpgradeUiState?,
    onNavigateBack: () -> Unit,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchase: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    val loaded = state as? UpgradeUiState.Loaded
    val isOwner = loaded?.ownership?.ownsAnything == true
    val inGrace = loaded?.grace != null

    UpgradeScreenShell(
        // Owners and grace users get the celebratory "Pro" title; acquisition gets the plain pitch title.
        title = if (isOwner || inGrace) {
            stringResource(R.string.app_name_upgraded)
        } else {
            stringResource(R.string.upgrade_screen_title)
        },
        postfix = stringResource(R.string.app_name_upgrade_postfix),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) {
        when {
            state == null || state is UpgradeUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 24.dp))
            }

            state is UpgradeUiState.Unavailable -> UnavailableContent(onRetry = onRetry)

            loaded != null && isOwner -> OwnerContent(
                state = loaded,
                onGoIap = onGoIap,
                onManageSubscription = onManageSubscription,
                onRestorePurchase = onRestorePurchase,
            )

            loaded != null && loaded.grace != null -> GraceContent(
                state = loaded,
                onGoIap = onGoIap,
                onGoSubscription = onGoSubscription,
                onGoSubscriptionTrial = onGoSubscriptionTrial,
                onRestorePurchase = onRestorePurchase,
            )

            loaded != null -> AcquisitionContent(
                state = loaded,
                onGoIap = onGoIap,
                onGoSubscription = onGoSubscription,
                onGoSubscriptionTrial = onGoSubscriptionTrial,
                onRestorePurchase = onRestorePurchase,
            )
        }
    }
}

// ---- Owner (Pro status) ----

@Composable
private fun OwnerContent(
    state: UpgradeUiState.Loaded,
    onGoIap: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val ownership = state.ownership
    val subscription = ownership.subscription

    BenefitsRecapCard()

    if (ownership.hasIap) {
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            icon = Icons.TwoTone.Stars,
            title = stringResource(R.string.upgrade_screen_owned_iap_title),
            body = stringResource(R.string.upgrade_screen_owned_iap_body),
        )
    }

    if (subscription != null) {
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            icon = Icons.TwoTone.Autorenew,
            title = stringResource(R.string.upgrade_screen_owned_sub_title),
            body = stringResource(
                if (subscription.isAutoRenewing) {
                    R.string.upgrade_screen_owned_sub_renewing_body
                } else {
                    R.string.upgrade_screen_owned_sub_not_renewing_body
                },
            ),
        ) {
            if (subscription.isAutoRenewing && ownership.hasIap) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_both_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onManageSubscription,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription))
            }
        }
    }

    // The switch offer: any subscriber who doesn't own the one-time purchase. Locked while the
    // subscription is still set to renew (buying now would double-bill); unlocked once it won't renew.
    if (subscription != null && !ownership.hasIap) {
        val switchUnlocked = !subscription.isAutoRenewing
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.upgrade_screen_switch_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.iapPrice?.let { price ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.upgrade_screen_iap_action_hint, price),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (switchUnlocked) {
                            R.string.upgrade_screen_switch_purchase_note
                        } else {
                            R.string.upgrade_screen_switch_locked_note
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onGoIap,
                    enabled = switchUnlocked && !state.verificationInProgress && !state.restoreInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.verificationInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(text = stringResource(R.string.upgrade_screen_iap_action))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    StatusRestoreSection(
        restoreInProgress = state.restoreInProgress,
        verificationInProgress = state.verificationInProgress,
        onRestorePurchase = onRestorePurchase,
    )
}

// ---- Grace (Pro active, Play can't confirm) ----

@Composable
private fun GraceContent(
    state: UpgradeUiState.Loaded,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val grace = state.grace ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (grace.showDiagnostics) {
                Icon(imageVector = Icons.TwoTone.Verified, contentDescription = null)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_grace_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (grace.showDiagnostics) {
                        R.string.upgrade_screen_grace_body
                    } else {
                        R.string.upgrade_screen_grace_body_short
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    // The quiet stage stays calm. The aged (diagnostics) stage offers a restore escape hatch AND
    // re-surfaces the purchase offers, so a genuinely-lapsed subscriber can re-subscribe or switch to
    // the one-time purchase without waiting the grace window out.
    if (grace.showDiagnostics) {
        Spacer(modifier = Modifier.height(16.dp))
        RestoreButton(
            restoreInProgress = state.restoreInProgress,
            enabled = !state.restoreInProgress && !state.verificationInProgress,
            onRestorePurchase = onRestorePurchase,
            filled = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OfferButtons(
            state = state,
            onGoIap = onGoIap,
            onGoSubscription = onGoSubscription,
            onGoSubscriptionTrial = onGoSubscriptionTrial,
        )
    }
}

// ---- Unavailable (price query failed) ----

@Composable
private fun UnavailableContent(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrades_gplay_unavailable_error_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.upgrades_gplay_unavailable_error_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_retry_action))
    }
}

// ---- Acquisition (sales) ----

@Composable
private fun AcquisitionContent(
    state: UpgradeUiState.Loaded,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.upgrade_screen_preamble),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(14.dp),
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    BenefitsRecapCard()
    Spacer(modifier = Modifier.height(18.dp))

    if (state.wasPreviouslyPro) {
        RestoreBanner(
            onRestorePurchase = onRestorePurchase,
            restoreInProgress = state.restoreInProgress,
            verificationInProgress = state.verificationInProgress,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    OfferButtons(
        state = state,
        onGoIap = onGoIap,
        onGoSubscription = onGoSubscription,
        onGoSubscriptionTrial = onGoSubscriptionTrial,
    )

    Spacer(modifier = Modifier.height(16.dp))
    RestoreButton(
        restoreInProgress = state.restoreInProgress,
        enabled = !state.restoreInProgress && !state.verificationInProgress,
        onRestorePurchase = onRestorePurchase,
        filled = false,
    )
    PriceHint(stringResource(R.string.upgrade_screen_restore_purchase_message))
}

// The subscription / one-time / generic purchase buttons. Shared by the acquisition screen and the
// aged grace stage. Show/hide by whether an offer EXISTS (subscriptionAvailable/iapAvailable); enable
// by whether the action is currently usable (the busy-gated *Enabled flags) — using the busy-gated
// flags for visibility would swap the real buttons for the generic fallback during the unsettled
// window or while a restore/verify runs.
@Composable
private fun OfferButtons(
    state: UpgradeUiState.Loaded,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
) {
    val hasSub = state.subscriptionAvailable
    val hasIap = state.iapAvailable

    if (hasSub) {
        val isTrial = state.subscriptionAction == SubscriptionAction.TRIAL
        Button(
            onClick = if (isTrial) onGoSubscriptionTrial else onGoSubscription,
            enabled = state.subscriptionEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(imageVector = Icons.TwoTone.Stars, contentDescription = null)
            Text(
                text = stringResource(
                    if (isTrial) R.string.upgrade_screen_subscription_trial_action
                    else R.string.upgrade_screen_subscription_action,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        state.subscriptionPrice?.let { price ->
            PriceHint(stringResource(R.string.upgrade_screen_subscription_action_hint, price))
        }
    }

    if (hasSub && hasIap) Spacer(modifier = Modifier.height(12.dp))

    if (hasIap) {
        if (hasSub) {
            FilledTonalButton(
                onClick = onGoIap,
                enabled = state.iapEnabled,
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
                enabled = state.iapEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(imageVector = Icons.TwoTone.Stars, contentDescription = null)
                Text(
                    text = stringResource(R.string.upgrade_screen_iap_action),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        state.iapPrice?.let { price ->
            PriceHint(stringResource(R.string.upgrade_screen_iap_action_hint, price))
        }
    }

    if (!hasSub && !hasIap) {
        Button(
            onClick = onGoIap,
            enabled = !state.restoreInProgress && !state.verificationInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(imageVector = Icons.TwoTone.Stars, contentDescription = null)
            Text(
                text = stringResource(R.string.general_upgrade_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// ---- Shared bits ----

@Composable
private fun BenefitsRecapCard() {
    val benefits = listOf(
        UpgradeBenefitItem(Icons.TwoTone.Devices, stringResource(R.string.upgrade_benefit_unlimited_devices)),
        UpgradeBenefitItem(Icons.TwoTone.PlayCircle, stringResource(R.string.upgrade_benefit_connection_actions)),
        UpgradeBenefitItem(Icons.TwoTone.Palette, stringResource(R.string.upgrade_benefit_theme_customization)),
        UpgradeBenefitItem(Icons.TwoTone.Tune, stringResource(R.string.upgrade_benefit_power_controls)),
        UpgradeBenefitItem(Icons.TwoTone.Favorite, stringResource(R.string.upgrade_benefit_support)),
    )
    BenefitListCard(title = stringResource(R.string.upgrade_screen_why_title), benefits = benefits)
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    body: String,
    extra: @Composable (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            extra?.invoke()
        }
    }
}

@Composable
private fun StatusRestoreSection(
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
    onRestorePurchase: () -> Unit,
) {
    Text(
        text = stringResource(R.string.upgrade_screen_status_restore_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.upgrade_screen_status_restore_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(10.dp))
    RestoreButton(
        restoreInProgress = restoreInProgress,
        enabled = !restoreInProgress && !verificationInProgress,
        onRestorePurchase = onRestorePurchase,
        filled = false,
    )
}

@Composable
private fun RestoreButton(
    restoreInProgress: Boolean,
    enabled: Boolean,
    onRestorePurchase: () -> Unit,
    filled: Boolean,
) {
    val content: @Composable () -> Unit = {
        if (restoreInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
        }
    }
    if (filled) {
        Button(
            onClick = onRestorePurchase,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onRestorePurchase,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { content() }
    }
}

@Composable
private fun PriceHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun RestoreBanner(
    onRestorePurchase: () -> Unit,
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            RestoreButton(
                restoreInProgress = restoreInProgress,
                enabled = !restoreInProgress && !verificationInProgress,
                onRestorePurchase = onRestorePurchase,
                filled = true,
            )
        }
    }
}

// ---- Dialogs ----

@Composable
fun RestoreFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.general_error_label),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.ok)) }
        },
        text = {
            Text(
                text = """
                    ${stringResource(R.string.upgrade_screen_restore_purchase_message)}

                    ${stringResource(R.string.upgrade_screen_restore_troubleshooting_msg)}

                    ${stringResource(R.string.upgrade_screen_restore_sync_patience_hint)}

                    ${stringResource(R.string.upgrade_screen_restore_multiaccount_hint)}
                """.trimIndent(),
            )
        },
    )
}

@Composable
fun SubscriptionStillRenewingDialog(
    onManageSubscription: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upgrade_screen_sub_still_renewing_title)) },
        text = { Text(stringResource(R.string.upgrade_screen_sub_still_renewing_message)) },
        confirmButton = {
            TextButton(onClick = {
                onManageSubscription()
                onDismiss()
            }) {
                Text(stringResource(R.string.upgrade_screen_manage_subscription))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.cancel)) }
        },
    )
}

@Composable
fun SubscriptionCheckFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.general_error_label)) },
        text = { Text(stringResource(R.string.upgrade_screen_sub_check_failed_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.ok)) }
        },
    )
}

// ---- Previews ----

private fun previewLoaded(
    ownership: Ownership = Ownership(),
    grace: GraceHint? = null,
    wasPreviouslyPro: Boolean = false,
) = UpgradeUiState.Loaded(
    subscriptionAction = SubscriptionAction.TRIAL,
    subscriptionEnabled = ownership.subscription == null,
    subscriptionPrice = "$2.99",
    iapEnabled = !ownership.hasIap,
    iapPrice = "$4.99",
    ownership = ownership,
    grace = grace,
    wasPreviouslyPro = wasPreviouslyPro,
)

@Preview2
@Composable
private fun UpgradeScreenAcquisitionPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenOwnerSubRenewingPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true))),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenOwnerSubNotRenewingPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false))),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenOwnerIapPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(ownership = Ownership(hasIap = true)),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenGraceQuietPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(grace = GraceHint(showDiagnostics = false)),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenGraceDiagnosticsPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = previewLoaded(grace = GraceHint(showDiagnostics = true)),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenUnavailablePreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = UpgradeUiState.Unavailable(RuntimeException("Play unavailable")),
            onNavigateBack = {}, onGoIap = {}, onGoSubscription = {},
            onGoSubscriptionTrial = {}, onManageSubscription = {}, onRestorePurchase = {}, onRetry = {},
        )
    }
}

@Preview2
@Composable
private fun RestoreFailedDialogPreview() {
    PreviewWrapper { RestoreFailedDialog(onDismiss = {}) }
}
