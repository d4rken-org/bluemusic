package eu.darken.bluemusic.upgrade.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.BlueMusicIcon
import eu.darken.bluemusic.common.compose.ColoredTitleText

internal data class UpgradeBenefitItem(
    val icon: ImageVector,
    val text: String,
)

@Composable
internal fun UpgradeScreenScaffold(
    title: String,
    postfix: String,
    preamble: String,
    benefitTitle: String,
    benefits: List<UpgradeBenefitItem>,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    UpgradeHero(
                        title = title,
                        postfix = postfix,
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = preamble,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }

                    Spacer(modifier = Modifier.size(14.dp))

                    BenefitListCard(
                        title = benefitTitle,
                        benefits = benefits,
                    )

                    Spacer(modifier = Modifier.size(18.dp))

                    actions()
                }
            }
        }

        FloatingBackButton(
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp),
        )

        if (snackbarHostState != null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FloatingBackButton(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onNavigateBack,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.general_back_action),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpgradeHero(
    title: String,
    postfix: String,
) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ) {}
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                BlueMusicIcon(size = 44.dp)
            }
        }
    }

    Spacer(modifier = Modifier.size(12.dp))

    ColoredTitleText(
        fullTitle = title,
        postfix = postfix,
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun BenefitListCard(
    title: String,
    benefits: List<UpgradeBenefitItem>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.size(10.dp))

            benefits.forEach { benefit ->
                BenefitRow(benefit = benefit)
            }
        }
    }
}

@Composable
private fun BenefitRow(benefit: UpgradeBenefitItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = benefit.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = benefit.text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
