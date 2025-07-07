package eu.darken.bluemusic.main.ui.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
internal fun PrivacyPage(
    onReadPrivacyPolicy: () -> Unit = {},
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.onboarding_privacy_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_privacy_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onReadPrivacyPolicy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_privacy_read_action))
            }

            Spacer(modifier = Modifier.height(24.dp))

        }

        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_privacy_action))
        }
    }
}

@Preview2
@Composable
private fun PrivacyPagePreview() {
    PreviewWrapper {
        PrivacyPage(
            onAccept = {},
        )
    }
}
