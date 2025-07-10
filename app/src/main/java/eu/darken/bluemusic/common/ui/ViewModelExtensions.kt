package eu.darken.bluemusic.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> waitForState(flow: Flow<T>): State<T?> = produceState(initialValue = null) {
    flow.collect { value = it }
}