package eu.darken.bluemusic.eq.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.eq.core.EqEvent
import eu.darken.bluemusic.eq.core.EqSession
import eu.darken.bluemusic.eq.core.EqSessionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Debug-only diagnostics screen, all texts are intentionally hardcoded English and not translated.

@Composable
fun EqSessionsScreenHost(vm: EqSessionsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()

    EqSessionsScreen(
        state = state,
        onListeningToggle = { vm.setListening(it) },
        onClear = { vm.clear() },
    )
}

@Composable
fun EqSessionsScreen(
    state: EqSessionState,
    onListeningToggle: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("EQ Sessions") }) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        val sessions = state.sessions.values.toList()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + navBarPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.listening) "Listening for effect sessions" else "Not listening",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION · generation ${state.generation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.listening,
                        onCheckedChange = onListeningToggle,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sessions (${sessions.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No sessions seen yet. Start listening, then play something.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(sessions, key = { it.sessionId }) { session -> SessionCard(session) }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Text(
                    text = "Events (${state.events.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(state.events.asReversed()) { event -> EventRow(event) }
        }
    }
}

@Composable
private fun SessionCard(session: EqSession) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = session.packageName ?: "unknown package",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append("session=${session.sessionId}")
                    append(" · gen=${session.generation}")
                    append(" · ")
                    append(if (session.closed) "closed" else "open")
                    append(" · ")
                    append(if (session.attached) "attached" else "detached")
                    session.hasControl?.let { append(" · control=$it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "opened ${TIME_FORMATTER.format(session.openedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(event: EqEvent) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${TIME_FORMATTER.format(event.time)}  ${event.type}",
            style = MaterialTheme.typography.bodySmall,
            color = when (event.type) {
                EqEvent.Type.MALFORMED, EqEvent.Type.ATTACH_FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        val subtitle = listOfNotNull(
            event.packageName,
            event.sessionId?.let { "session=$it" },
            event.detail.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@Preview2
@Composable
private fun EqSessionsScreenPreview() {
    val now = Instant.parse("2026-01-01T10:00:00Z")
    PreviewWrapper {
        EqSessionsScreen(
            state = EqSessionState(
                listening = true,
                generation = 3,
                sessions = mapOf(
                    42 to EqSession(
                        sessionId = 42,
                        generation = 3,
                        openedAt = now,
                        packageName = "com.spotify.music",
                        attached = true,
                        hasControl = true,
                    ),
                    7 to EqSession(
                        sessionId = 7,
                        generation = 3,
                        openedAt = now,
                        packageName = "com.soundcloud.android",
                        closed = true,
                    ),
                ),
                events = listOf(
                    EqEvent(time = now, type = EqEvent.Type.LISTENING, detail = "Registered receiver (gen=3)"),
                    EqEvent(
                        time = now,
                        type = EqEvent.Type.OPEN,
                        packageName = "com.spotify.music",
                        sessionId = 42,
                        detail = "New session",
                    ),
                    EqEvent(
                        time = now,
                        type = EqEvent.Type.MALFORMED,
                        packageName = "com.example.bad",
                        detail = "Missing session id extra",
                    ),
                ),
            ),
            onListeningToggle = {},
            onClear = {},
        )
    }
}
