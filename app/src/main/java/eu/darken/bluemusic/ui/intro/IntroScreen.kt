package eu.darken.bluemusic.ui.intro

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroScreen(
    state: IntroState,
    onEvent: (IntroEvent) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onNavigateToMainScreen: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(state.shouldClose) {
        if (state.shouldClose) {
            onNavigateToMainScreen()
        }
    }
    
    LaunchedEffect(state.needsBluetoothPermission) {
        if (state.needsBluetoothPermission) {
            onRequestBluetoothPermission()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = stringResource(R.string.label_intro_welcome),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_intro_what_does_this_app_do),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.description_intro_what_does_this_app_do),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_intro_permissions),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.description_intro_permissions),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Privacy Policy Link
            val annotatedString = buildAnnotatedString {
                append(stringResource(R.string.msg_intro_privacy_policy_text))
                append(" ")
                pushStringAnnotation(
                    tag = "privacy_policy",
                    annotation = "https://bluemusic.darken.eu/privacy"
                )
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(stringResource(R.string.label_privacy_policy))
                }
                pop()
            }
            
            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodySmall.copy(
                    textAlign = TextAlign.Center
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(
                        tag = "privacy_policy",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let { annotation ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open privacy policy")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { onEvent(IntroEvent.OnFinishOnboarding) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_finish_setup))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
private fun IntroScreenPreview() {
    BlueMusicTheme {
        IntroScreen(
            state = IntroState(),
            onEvent = {},
            onRequestBluetoothPermission = {},
            onNavigateToMainScreen = {}
        )
    }
}