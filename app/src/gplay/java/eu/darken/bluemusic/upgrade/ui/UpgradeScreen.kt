package eu.darken.bluemusic.upgrade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.BlueMusicIcon
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.ui.waitForState


@Composable
fun UpgradeScreenHost(vm: UpgradeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var showRestoreFailedDialog by remember { mutableStateOf(false) }

    ErrorEventHandler(vm)

    val state by waitForState(vm.state)
    log(vm.tag) { "Screen state: $state" }
    log(vm.tag) { "showRestoreFailedDialog: $showRestoreFailedDialog" }

    state?.let { state ->
        UpgradeScreen(
            state = state,
            onNavigateBack = { vm.navUp() },
            onGoIap = { vm.onGoIap(context as android.app.Activity) },
            onGoSubscription = { vm.onGoSubscription(context as android.app.Activity) },
            onGoSubscriptionTrial = { vm.onGoSubscriptionTrial(context as android.app.Activity) },
            onRestorePurchase = { vm.restorePurchase() },
        )
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event: UpgradeEvents ->
            when (event) {
                UpgradeEvents.RestoreFailed -> {
                    log(vm.tag) { "Received RestoreFailed event. Setting showRestoreFailedDialog to true." }
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
    state: UpgradeViewModel.State,
    onNavigateBack: () -> Unit,
    onGoIap: () -> Unit,
    onGoSubscription: () -> Unit,
    onGoSubscriptionTrial: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollProgress by remember {
        derivedStateOf { (scrollState.value / 200f).coerceIn(0f, 1f) }
    }

    val toolbarAlpha by animateFloatAsState(
        targetValue = scrollProgress,
        animationSpec = tween(300),
        label = "toolbarAlpha"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = 1f - scrollProgress,
        animationSpec = tween(300),
        label = "contentAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = scrollProgress > 0.5f
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BlueMusicIcon(
                                modifier = Modifier.graphicsLayer(alpha = toolbarAlpha),
                                size = 32.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            ColoredTitleText(
                                fullTitle = stringResource(R.string.app_name_upgraded),
                                postfix = stringResource(R.string.app_name_upgrade_postfix),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer(alpha = toolbarAlpha)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp)
                .padding(top = 8.dp, bottom = 32.dp)
                .animateContentSize(animationSpec = tween(300))
        ) {
            BlueMusicIcon(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer(alpha = contentAlpha),
                size = 72.dp
            )

            ColoredTitleText(
                fullTitle = stringResource(R.string.app_name_upgraded),
                postfix = stringResource(R.string.app_name_upgrade_postfix),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer(alpha = contentAlpha),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = stringResource(R.string.upgrade_screen_preamble),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = stringResource(R.string.upgrade_screen_why_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.upgrade_screen_why_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = stringResource(R.string.upgrade_screen_how_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.upgrade_screen_how_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
            )

            // Subscription Button
            AnimatedVisibility(
                visible = state.subState.available || state.trialState.available,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 400))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (state.trialState.available) {
                        Button(
                            onClick = onGoSubscriptionTrial,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.upgrade_screen_subscription_trial_action),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onGoSubscription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(text = stringResource(R.string.upgrade_screen_subscription_action))
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.upgrade_screen_subscription_action_hint,
                            state.subState.formattedPrice ?: ""
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // One-time Purchase Button
            AnimatedVisibility(
                visible = state.iapState.available,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 500))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    OutlinedButton(
                        onClick = onGoIap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(text = stringResource(R.string.upgrade_screen_iap_action))
                    }

                    Text(
                        text = stringResource(
                            R.string.upgrade_screen_iap_action_hint,
                            state.iapState.formattedPrice ?: ""
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Restore Purchase Button
            TextButton(
                onClick = onRestorePurchase,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
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