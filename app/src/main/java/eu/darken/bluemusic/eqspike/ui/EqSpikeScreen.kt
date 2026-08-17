package eu.darken.bluemusic.eqspike.ui

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
import androidx.compose.foundation.layout.width
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
import eu.darken.bluemusic.eqspike.core.EqSpikeState
import eu.darken.bluemusic.eqspike.core.SpikeEvent
import eu.darken.bluemusic.eqspike.core.SpikeSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Debug-only spike screen, all texts are intentionally hardcoded English and not translated.

@Composable
fun EqSpikeScreenHost(vm: EqSpikeViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()

    EqSpikeScreen(
        state = state,
        onListeningToggle = { if (it) vm.startListening() else vm.stopListening() },
        onClear = { vm.clear() },
        onAttach = { session -> vm.attach(session.packageName, session.sessionId) },
        onDetach = { session -> vm.detach(session.packageName, session.sessionId) },
    )
}

@Composable
fun EqSpikeScreen(
    state: EqSpikeState,
    onListeningToggle: (Boolean) -> Unit,
    onClear: () -> Unit,
    onAttach: (SpikeSession) -> Unit,
    onDetach: (SpikeSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("EQ Spike") }) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
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
                            text = "OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION",
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
                        text = "Sessions (${state.sessions.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        text = "No sessions seen yet. Start listening, then play something.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.sessions, key = { "${it.packageName}:${it.sessionId}" }) { session ->
                SessionCard(
                    session = session,
                    onAttach = { onAttach(session) },
                    onDetach = { onDetach(session) },
                )
            }

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
private fun SessionCard(
    session: SpikeSession,
    onAttach: () -> Unit,
    onDetach: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildString {
                        append("session=${session.sessionId}")
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
            Spacer(modifier = Modifier.width(8.dp))
            if (session.attached) {
                TextButton(onClick = onDetach) { Text("Detach") }
            } else {
                TextButton(onClick = onAttach, enabled = !session.closed) { Text("Attach") }
            }
        }
    }
}

@Composable
private fun EventRow(event: SpikeEvent) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${TIME_FORMATTER.format(event.time)}  ${event.type}",
            style = MaterialTheme.typography.bodySmall,
            color = when (event.type) {
                SpikeEvent.Type.MALFORMED, SpikeEvent.Type.ATTACH_FAILED -> MaterialTheme.colorScheme.error
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
private fun EqSpikeScreenPreview() {
    val now = Instant.parse("2026-01-01T10:00:00Z")
    PreviewWrapper {
        EqSpikeScreen(
            state = EqSpikeState(
                listening = true,
                sessions = listOf(
                    SpikeSession(
                        packageName = "com.spotify.music",
                        sessionId = 42,
                        openedAt = now,
                        attached = true,
                        hasControl = true,
                    ),
                    SpikeSession(
                        packageName = "com.soundcloud.android",
                        sessionId = 7,
                        openedAt = now,
                        closed = true,
                    ),
                ),
                events = listOf(
                    SpikeEvent(time = now, type = SpikeEvent.Type.LISTENING, detail = "Registered receiver"),
                    SpikeEvent(
                        time = now,
                        type = SpikeEvent.Type.OPEN,
                        packageName = "com.spotify.music",
                        sessionId = 42,
                        detail = "New session",
                    ),
                    SpikeEvent(
                        time = now,
                        type = SpikeEvent.Type.MALFORMED,
                        packageName = "com.example.bad",
                        detail = "Missing session id extra",
                    ),
                ),
            ),
            onListeningToggle = {},
            onClear = {},
            onAttach = {},
            onDetach = {},
        )
    }
}
